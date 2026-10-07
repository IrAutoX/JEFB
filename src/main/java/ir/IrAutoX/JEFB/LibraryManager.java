package ir.IrAutoX.JEFB;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class LibraryManager {

    static final String CACHE_LIB_DIR = JBuild.SETTINGS_DIR
            + File.separator + "cache" + File.separator + "libs";
    static final String MAVEN_BASE = "https://repo1.maven.org/maven2/";

    public static List<Path> listJars(Project p) throws IOException {
        List<Path> out = new ArrayList<>();
        Path libDir = p.root.resolve("lib");
        if (!Files.isDirectory(libDir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(libDir, "*.jar")) {
            for (Path j : ds) out.add(j);
        }
        Collections.sort(out);
        return out;
    }

    public static String buildClasspath(Project p) throws IOException {
        StringBuilder cp = new StringBuilder();
        for (Path j : listJars(p)) {
            if (cp.length() > 0) cp.append(File.pathSeparator);
            cp.append(j.toAbsolutePath().toString());
        }
        String extra = p.libraries();
        if (!extra.isBlank()) {
            for (String s : extra.split(File.pathSeparator)) {
                if (s.isBlank()) continue;
                if (cp.length() > 0) cp.append(File.pathSeparator);
                cp.append(s.trim());
            }
        }
        return cp.toString();
    }

    public static Path bundledLibDir() {
        try {
            URL url = JBuild.class.getProtectionDomain().getCodeSource().getLocation();
            Path self = Paths.get(url.toURI());
            Path base = Files.isDirectory(self) ? self : self.getParent();
            return base.resolve("lib");
        } catch (Exception e) { return Paths.get("lib"); }
    }

    public static List<Path> bundledJars() {
        List<Path> out = new ArrayList<>();
        Path dir = bundledLibDir();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path j : ds) out.add(j);
        } catch (IOException ignored) {}
        Collections.sort(out);
        return out;
    }

    public static String bundledClasspath() {
        StringBuilder cp = new StringBuilder();
        for (Path j : bundledJars()) {
            if (cp.length() > 0) cp.append(File.pathSeparator);
            cp.append(j.toAbsolutePath().toString());
        }
        return cp.toString();
    }

    public static Path cacheDir() { return Paths.get(CACHE_LIB_DIR); }

    public static Path cachedJar(String group, String artifact, String version) {
        return cacheDir().resolve(group.replace('.', '/'))
                .resolve(artifact).resolve(version)
                .resolve(artifact + "-" + version + ".jar");
    }

    public static Path downloadMaven(String group, String artifact, String version) {
        try {
            Path target = cachedJar(group, artifact, version);
            if (Files.isRegularFile(target) && Files.size(target) > 0) return target;
            Files.createDirectories(target.getParent());
            String url = MAVEN_BASE + group.replace('.', '/') + "/"
                    + artifact + "/" + version + "/"
                    + artifact + "-" + version + ".jar";
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2)).GET().build();
            HttpResponse<Path> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() != 200) {
                Files.deleteIfExists(target);
                return null;
            }
            return target;
        } catch (Exception e) {
            return null;
        }
    }

    private LibraryManager() {}
}