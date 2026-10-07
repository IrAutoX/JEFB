package ir.IrAutoX.JEFB;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class Plugin {

    static final String PLUGINS_DIR = JBuild.SETTINGS_DIR + File.separator + "plugins";

    public static class Info {
        public final Path jar;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final String mainClass;
        public final boolean trusted;
        public final String publisher;

        public Info(Path jar, String name, String version, String author,
                    String description, String mainClass, boolean trusted,
                    String publisher) {
            this.jar = jar;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.mainClass = mainClass;
            this.trusted = trusted;
            this.publisher = publisher;
        }

        @Override public String toString() {
            return (trusted ? "[Trusted] " : "[Untrusted] ")
                    + name + " v" + version;
        }
    }

    public static List<Info> discover() {
        List<Info> out = new ArrayList<>();
        Path dir = Paths.get(PLUGINS_DIR);
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path j : ds) {
                Info i = readInfo(j);
                if (i != null) out.add(i);
            }
        } catch (IOException ignored) {}
        return out;
    }

    public static Info readInfo(Path jar) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = zf.getEntry("META-INF/JEFB.PLUGIN");
            if (e == null) return null;
            String content;
            try (InputStream is = zf.getInputStream(e)) {
                content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            Properties p = new Properties();
            try (Reader r = new StringReader(content)) { p.load(r); }

            TrustedPublisher.VerifyResult vr = TrustedPublisher.verify(jar);

            return new Info(jar,
                    p.getProperty("name", jar.getFileName().toString()),
                    p.getProperty("version", "0.0.0"),
                    p.getProperty("author", "unknown"),
                    p.getProperty("description", ""),
                    p.getProperty("main", ""),
                    vr.isTrusted(),
                    vr.company);
        } catch (Exception e) { return null; }
    }

    public static void install(Path jar) throws IOException {
        Files.createDirectories(Paths.get(PLUGINS_DIR));
        Path dst = Paths.get(PLUGINS_DIR).resolve(jar.getFileName().toString());
        Files.copy(jar, dst, StandardCopyOption.REPLACE_EXISTING);
        PluginRuntime.load(dst);
    }

    public static void uninstall(Path jar) throws IOException {
        Files.deleteIfExists(jar);
    }

    public static void trust(Path jar) throws IOException {
        TrustedPublisher.trustJar(jar);
    }

    private Plugin() {}
}