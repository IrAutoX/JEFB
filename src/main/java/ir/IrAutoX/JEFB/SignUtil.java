package ir.IrAutoX.JEFB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SignUtil {

    static final String SIGN_FILE = "build/.jefb-sign";
    static final String META_FILE = "build/.jefb-meta";

    public static String projectHash(Project p) throws IOException {
        Path classes = p.classesPath();
        if (!Files.isDirectory(classes)) return "";
        List<String> lines = new ArrayList<>();
        Files.walkFileTree(classes, new java.nio.file.SimpleFileVisitor<>() {
            @Override public java.nio.file.FileVisitResult visitFile(
                    Path f, java.nio.file.attribute.BasicFileAttributes a) {
                try { lines.add(IO.rel(classes, f) + ":" + Crypto.sha256(f)); }
                catch (IOException ignored) {}
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(lines);
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append("\n");
        return Crypto.sha256Bytes(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String buildSignature(SignMeta m) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("company=").append(m.company).append(";");
        sb.append("author=").append(m.author).append(";");
        sb.append("issued=").append(m.issued).append(";");
        sb.append("expires=").append(m.expires).append(";");
        sb.append("license=").append(m.license).append(";");
        sb.append("projectHash=").append(m.projectHash);
        return Crypto.hmac(sb.toString());
    }

    public static boolean verifyMeta(SignMeta m) throws IOException {
        if (m.signature == null || m.signature.isBlank()) return false;
        return buildSignature(m).equals(m.signature);
    }

    public static boolean isExpired(SignMeta m) {
        if (m.expires == null || m.expires.isBlank()) return false;
        try {
            return LocalDate.now().isAfter(
                    LocalDate.parse(m.expires, DateTimeFormatter.ISO_DATE));
        } catch (Exception e) { return true; }
    }

    public static void saveMeta(Project p, SignMeta m) throws IOException {
        Crypto.writeEncrypted(p.root.resolve(META_FILE), m.serialize());
    }

    public static SignMeta loadMeta(Project p) throws IOException {
        String s = Crypto.readEncrypted(p.root.resolve(META_FILE));
        return s == null ? null : SignMeta.parse(s);
    }

    public static void saveSignatures(Project p, Map<String, String> sigs) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# JEFB signature file v3\n");
        List<String> keys = new ArrayList<>(sigs.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            String v = sigs.get(k);
            sb.append(k).append("=").append(v).append("|")
              .append(Crypto.hmac(k + ":" + v)).append("\n");
        }
        Crypto.writeEncrypted(p.root.resolve(SIGN_FILE), sb.toString());
    }

    public static Map<String, String> loadSignatures(Project p) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        String content = Crypto.readEncrypted(p.root.resolve(SIGN_FILE));
        if (content == null) return out;
        for (String line : content.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            int pipe = line.lastIndexOf('|');
            if (eq < 0 || pipe < 0 || pipe < eq) continue;
            String rel = line.substring(0, eq);
            String hash = line.substring(eq + 1, pipe);
            String mac = line.substring(pipe + 1);
            try {
                if (Crypto.hmac(rel + ":" + hash).equals(mac)) out.put(rel, hash);
            } catch (IOException ignored) {}
        }
        return out;
    }

    public static boolean verify(Project p) throws IOException {
        Map<String, String> sigs = loadSignatures(p);
        if (sigs.isEmpty()) return false;
        Path classes = p.classesPath();
        for (Map.Entry<String, String> e : sigs.entrySet()) {
            Path f = classes.resolve(e.getKey());
            if (!Files.isRegularFile(f)) return false;
            if (!Crypto.sha256(f).equals(e.getValue())) return false;
        }
        SignMeta m = loadMeta(p);
        if (m != null) {
            if (!verifyMeta(m)) return false;
            if (isExpired(m)) return false;
        }
        return true;
    }

    public static boolean verifyJar(Path jar) throws IOException {
        String sigContent = null;
        String metaContent = null;
        String pubContent = null;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze.isDirectory()) continue;
                try (InputStream is = zf.getInputStream(ze)) {
                    byte[] data = is.readAllBytes();
                    String n = ze.getName();
                    if (n.equals("META-INF/JEFB.SIG"))
                        sigContent = new String(data, StandardCharsets.UTF_8);
                    else if (n.equals("META-INF/JEFB.META"))
                        metaContent = new String(data, StandardCharsets.UTF_8);
                    else if (n.equals("META-INF/JEFB.PUB"))
                        pubContent = new String(data, StandardCharsets.UTF_8);
                    else if (!n.startsWith("META-INF/"))
                        entries.put(n, data);
                }
            }
        }
        if (sigContent == null) return false;

        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : sigContent.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            int pipe = line.lastIndexOf('|');
            if (eq < 0 || pipe < 0 || pipe < eq) continue;
            String rel = line.substring(0, eq);
            String hash = line.substring(eq + 1, pipe);
            String mac = line.substring(pipe + 1);
            try {
                if (!Crypto.hmac(rel + ":" + hash).equals(mac)) return false;
            } catch (IOException e) { return false; }
            expected.put(rel, hash);
        }
        if (expected.isEmpty()) return false;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String want = expected.get(e.getKey());
            if (want == null) return false;
            if (!Crypto.sha256Bytes(e.getValue()).equals(want)) return false;
        }
        if (metaContent != null) {
            SignMeta m = SignMeta.parse(metaContent);
            try {
                if (!verifyMeta(m)) return false;
                if (isExpired(m)) return false;
            } catch (IOException e) { return false; }
        }
        return true;
    }

    private SignUtil() {}
}