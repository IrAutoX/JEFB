package ir.IrAutoX.JEFB;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Crypto {

    private static final byte[] STATIC_KEY = new byte[]{
        0x4a, 0x45, 0x46, 0x42, 0x2d, 0x49, 0x72, 0x41, 0x75, 0x74,
        0x6f, 0x58, 0x2d, 0x44, 0x65, 0x61, 0x74, 0x68, 0x41, 0x6d,
        0x69, 0x72, 0x2d, 0x32, 0x30, 0x32, 0x35, 0x2d, 0x4a, 0x45,
        0x46, 0x42, 0x2d, 0x56, 0x36, 0x2d, 0x41, 0x45, 0x53, 0x00
    };

    private static final String AES_FILE = System.getProperty("user.home")
            + java.io.File.separator + ".jefb" + java.io.File.separator + "aes.key";

    public static byte[] aesKey() {
        try {
            Path kf = Paths.get(AES_FILE);
            if (Files.isRegularFile(kf)) {
                return Base64.getDecoder().decode(IO.read(kf).trim());
            }
            byte[] k = new byte[32];
            new SecureRandom().nextBytes(k);
            IO.write(kf, Base64.getEncoder().encodeToString(k));
            return k;
        } catch (Exception e) {
            return Arrays.copyOf(STATIC_KEY, 32);
        }
    }

    public static String sha256(Path p) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(Files.readAllBytes(p)));
        } catch (Exception e) {
            throw new IOException("SHA-256 failed", e);
        }
    }

    public static String sha256Bytes(byte[] data) throws IOException {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IOException("SHA-256 failed", e);
        }
    }

    /** Convenience SHA-256 of a string (UTF-8). */
    public static String sha256String(String s) {
        try {
            return sha256Bytes(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "unavailable"; // cannot happen for in-memory input
        }
    }

    public static String hmac(String data) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(STATIC_KEY, "HmacSHA256"));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("HMAC failed", e);
        }
    }

    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static byte[] gzip(byte[] in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(in); }
        return bos.toByteArray();
    }

    public static byte[] gunzip(byte[] in) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(in))) {
            return gz.readAllBytes();
        }
    }

    public static byte[] aesEncrypt(byte[] data) throws IOException {
        try {
            byte[] key = aesKey();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(data);
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return out;
        } catch (Exception e) {
            throw new IOException("AES encrypt failed", e);
        }
    }

    public static byte[] aesDecrypt(byte[] data) throws IOException {
        try {
            if (data.length < 13) throw new IOException("Ciphertext too short");
            byte[] key = aesKey();
            byte[] iv = Arrays.copyOfRange(data, 0, 12);
            byte[] enc = Arrays.copyOfRange(data, 12, data.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            return c.doFinal(enc);
        } catch (Exception e) {
            throw new IOException("AES decrypt failed", e);
        }
    }

    public static void writeEncrypted(Path p, String content) throws IOException {
        byte[] gz = gzip(content.getBytes(StandardCharsets.UTF_8));
        byte[] enc = aesEncrypt(gz);
        IO.write(p, Base64.getEncoder().encodeToString(enc));
    }

    public static String readEncrypted(Path p) throws IOException {
        if (!Files.isRegularFile(p)) return null;
        byte[] enc = Base64.getDecoder().decode(IO.read(p).trim());
        byte[] gz = aesDecrypt(enc);
        return new String(gunzip(gz), StandardCharsets.UTF_8);
    }

    private Crypto() {}
}