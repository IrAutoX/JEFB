package ir.IrAutoX.JEFB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public final class TrustedPublisher {

    static final String TRUSTED_FILE = JBuild.SETTINGS_DIR
            + java.io.File.separator + "trusted.txt";
    static final String TRUSTED_KEYS = JBuild.SETTINGS_DIR
            + java.io.File.separator + "trusted-keys.txt";

    public static class Info {
        public final String company;
        public final String pubKeyB64;
        public final String pubKeySha256;

        public Info(String company, String pubKeyB64) throws IOException {
            this.company = company;
            this.pubKeyB64 = pubKeyB64;
            this.pubKeySha256 = Crypto.sha256Bytes(
                    Base64.getDecoder().decode(pubKeyB64));
        }

        @Override public String toString() {
            return company + " (" + pubKeySha256.substring(0, 16) + "...)";
        }
    }

    public static class VerifyResult {
        public final boolean signed;
        public final boolean signatureValid;
        public final boolean keyTrusted;
        public final String company;
        public final String pubKeySha256;
        public final String message;

        VerifyResult(boolean signed, boolean signatureValid, boolean keyTrusted,
                     String company, String pubKeySha256, String message) {
            this.signed = signed;
            this.signatureValid = signatureValid;
            this.keyTrusted = keyTrusted;
            this.company = company;
            this.pubKeySha256 = pubKeySha256;
            this.message = message;
        }

        public boolean isTrusted() {
            return signed && signatureValid && keyTrusted;
        }
    }

    public static VerifyResult verify(Path jar) {
        try {
            String sigContent = null;
            String pubB64 = null;
            String metaContent = null;
            Map<String, byte[]> entries = new LinkedHashMap<>();

            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar.toFile())) {
                Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry ze = en.nextElement();
                    if (ze.isDirectory()) continue;
                    try (java.io.InputStream is = zf.getInputStream(ze)) {
                        byte[] data = is.readAllBytes();
                        String n = ze.getName();
                        if (n.equals("META-INF/JEFB.SIG"))
                            sigContent = new String(data, StandardCharsets.UTF_8);
                        else if (n.equals("META-INF/JEFB.PUB"))
                            pubB64 = new String(data, StandardCharsets.UTF_8).trim();
                        else if (n.equals("META-INF/JEFB.META"))
                            metaContent = new String(data, StandardCharsets.UTF_8);
                        else if (!n.startsWith("META-INF/"))
                            entries.put(n, data);
                    }
                }
            }

            if (sigContent == null || pubB64 == null) {
                return new VerifyResult(false, false, false, null, null,
                        "Not signed");
            }

            StringBuilder payloadBuilder = new StringBuilder();
            boolean inEntries = false;
            String sigB64 = null;
            for (String line : sigContent.split("\\R")) {
                if (line.equals("entries:")) { inEntries = true; continue; }
                if (inEntries) {
                    if (line.startsWith("signature=")) {
                        sigB64 = line.substring("signature=".length());
                        inEntries = false;
                    } else {
                        payloadBuilder.append(line).append("\n");
                    }
                }
            }

            if (sigB64 == null) {
                return new VerifyResult(true, false, false, null, null,
                        "Signature block missing");
            }

            byte[] pubBytes = Base64.getDecoder().decode(pubB64);
            PublicKey pk = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(pubBytes));

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pk);
            sig.update(payloadBuilder.toString().getBytes(StandardCharsets.UTF_8));
            boolean valid = sig.verify(Base64.getDecoder().decode(sigB64));

            if (!valid) {
                return new VerifyResult(true, false, false, null, null,
                        "Invalid RSA signature");
            }

            Map<String, String> expected = new LinkedHashMap<>();
            for (String line : payloadBuilder.toString().split("\\R")) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                expected.put(line.substring(0, eq), line.substring(eq + 1));
            }

            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String want = expected.get(e.getKey());
                if (want == null) {
                    return new VerifyResult(true, false, false, null, null,
                            "Missing hash for " + e.getKey());
                }
                if (!Crypto.sha256Bytes(e.getValue()).equals(want)) {
                    return new VerifyResult(true, false, false, null, null,
                            "Hash mismatch: " + e.getKey());
                }
            }

            String company = null;
            if (metaContent != null) {
                for (String line : metaContent.split("\\R")) {
                    if (line.startsWith("company="))
                        company = line.substring("company=".length()).trim();
                }
            }

            String pubKeySha = Crypto.sha256Bytes(pubBytes);
            boolean keyTrusted = isKeyTrusted(pubKeySha);

            String msg = keyTrusted
                    ? "Trusted publisher: " + (company == null ? "unknown" : company)
                    : "Signature valid but publisher NOT trusted";

            return new VerifyResult(true, true, keyTrusted, company, pubKeySha, msg);
        } catch (Exception e) {
            return new VerifyResult(false, false, false, null, null,
                    "Verify error: " + Log.msg(e));
        }
    }

    public static boolean isKeyTrusted(String pubKeySha256) {
        try {
            Path kf = Paths.get(TRUSTED_KEYS);
            if (!Files.isRegularFile(kf)) return false;
            for (String line : Files.readAllLines(kf, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 1 && parts[0].trim().equals(pubKeySha256))
                    return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static List<Info> listTrusted() {
        List<Info> out = new ArrayList<>();
        try {
            Path kf = Paths.get(TRUSTED_KEYS);
            if (!Files.isRegularFile(kf)) return out;
            for (String line : Files.readAllLines(kf, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    try { out.add(new Info(parts[1].trim(), parts[2].trim())); }
                    catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void trustKey(String company, String pubKeyB64) throws IOException {
        String sha = Crypto.sha256Bytes(Base64.getDecoder().decode(pubKeyB64));
        Path kf = Paths.get(TRUSTED_KEYS);
        List<String> existing = Files.isRegularFile(kf)
                ? Files.readAllLines(kf, StandardCharsets.UTF_8)
                : new ArrayList<>();
        for (String line : existing) {
            if (line.startsWith(sha + "|")) return;
        }
        existing.add(sha + "|" + company + "|" + pubKeyB64);
        Files.write(kf, existing, StandardCharsets.UTF_8);
    }

    public static void untrustKey(String pubKeyB64) throws IOException {
        String sha = Crypto.sha256Bytes(Base64.getDecoder().decode(pubKeyB64));
        Path kf = Paths.get(TRUSTED_KEYS);
        if (!Files.isRegularFile(kf)) return;
        List<String> lines = Files.readAllLines(kf, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith(sha + "|")) out.add(line);
        }
        Files.write(kf, out, StandardCharsets.UTF_8);
    }

    public static void trustJar(Path jar) throws IOException {
        VerifyResult r = verify(jar);
        if (!r.signed || !r.signatureValid) {
            throw new IOException("Cannot trust: not a valid signature");
        }
        String pub = null;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar.toFile())) {
            java.util.zip.ZipEntry e = zf.getEntry("META-INF/JEFB.PUB");
            if (e != null) {
                try (java.io.InputStream is = zf.getInputStream(e)) {
                    pub = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            }
        }
        if (pub == null) throw new IOException("No public key in JAR");
        String company = r.company == null ? "unknown" : r.company;
        trustKey(company, pub);
    }

    private TrustedPublisher() {}
}