package ir.IrAutoX.JEFB;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class PackageRepository {

    static final String LIB_REPO_URL    = "https://api.github.com/repos/IrAutoX/JEFBL/contents";
    static final String LIB_RAW_BASE    = "https://raw.githubusercontent.com/IrAutoX/JEFBL/main";
    static final String PLUGIN_REPO_URL = "https://api.github.com/repos/IrAutoX/JEFBL/contents";
    static final String PLUGIN_RAW_BASE = "https://raw.githubusercontent.com/IrAutoX/JEFBL/main";

    public static class Item {
        public final String name;
        public final String url;
        public final String repoBase;
        public final long size;

        public Item(String name, String url, String repoBase, long size) {
            this.name = name;
            this.url = url;
            this.repoBase = repoBase;
            this.size = size;
        }

        @Override public String toString() {
            return name + (size > 0 ? "  (" + (size / 1024) + " KB)" : "");
        }
    }

    public static List<Item> listLibs() {
        return list(LIB_REPO_URL, LIB_RAW_BASE);
    }

    public static List<Item> listPlugins() {
        return list(PLUGIN_REPO_URL, PLUGIN_RAW_BASE);
    }

    static List<Item> list(String apiUrl, String rawBase) {
        List<Item> out = new ArrayList<>();
        try {
            Log.raw("[pkg] GET " + apiUrl);
            HttpClient c = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
                    .header("User-Agent", "JEFB-PM")
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
            Log.raw("[pkg] HTTP " + resp.statusCode() + " (" + resp.body().length() + " bytes)");

            if (resp.statusCode() != 200) {
                Log.warn("[pkg] Bad status: " + resp.statusCode());
                return out;
            }

            String json = resp.body();
            int idx = 0;
            while (true) {
                int nameIdx = json.indexOf("\"name\":", idx);
                if (nameIdx < 0) break;
                int q1 = json.indexOf('"', nameIdx + 7);
                int q2 = json.indexOf('"', q1 + 1);
                if (q1 < 0 || q2 < 0) break;
                String name = json.substring(q1 + 1, q2);

                int nextBrace = json.indexOf('}', q2);

                long size = 0;
                int sizeIdx = json.indexOf("\"size\":", q2);
                if (sizeIdx > 0 && sizeIdx < nextBrace) {
                    int colon = sizeIdx + 7;
                    StringBuilder sb = new StringBuilder();
                    for (int i = colon; i < nextBrace; i++) {
                        char ch = json.charAt(i);
                        if (Character.isDigit(ch)) sb.append(ch);
                        else if (sb.length() > 0) break;
                    }
                    try { size = Long.parseLong(sb.toString()); } catch (Exception ignored) {}
                }

                String url = rawBase + "/" + name;
                int dlIdx = json.indexOf("\"download_url\":", q2);
                if (dlIdx > 0 && dlIdx < nextBrace) {
                    int colon = dlIdx + 15;
                    int qq1 = json.indexOf('"', colon);
                    if (qq1 > 0 && qq1 < nextBrace) {
                        int qq2 = json.indexOf('"', qq1 + 1);
                        if (qq2 > qq1 && qq2 < nextBrace) {
                            String v = json.substring(qq1 + 1, qq2);
                            if (!v.isBlank()) url = v;
                        }
                    }
                }

                if (name.toLowerCase().endsWith(".jar")) {
                    out.add(new Item(name, url, rawBase, size));
                    Log.raw("[pkg]   + " + name + " (" + size + " bytes)");
                }
                idx = q2 + 1;
            }
            Log.raw("[pkg] Total: " + out.size());
        } catch (Exception e) {
            Log.error("[pkg] Failed: " + Log.msg(e));
        }
        return out;
    }

    public static Path download(Item item, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(item.name);
        HttpClient c = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(item.url))
                .header("User-Agent", "JEFB-PM")
                .timeout(Duration.ofMinutes(5)).GET().build();
        try {
            HttpResponse<Path> resp = c.send(req, HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() != 200) {
                Files.deleteIfExists(target);
                throw new IOException("HTTP " + resp.statusCode());
            }
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    public static void installLib(Item item, Project p) throws IOException {
        Path libDir = p.root.resolve("lib");
        Path downloaded = download(item, libDir);
        updateProjectLibraries(p);
        Log.ok("Installed library: " + downloaded.getFileName());
    }

    public static void installPlugin(Item item) throws IOException {
        Path dir = Paths.get(JBuild.PLUGINS_DIR);
        Files.createDirectories(dir);
        Path downloaded = download(item, dir);
        if (TrustedPublisher.verify(downloaded).isTrusted()) {
            PluginRuntime.load(downloaded);
            Log.ok("Installed trusted plugin: " + downloaded.getFileName());
        } else {
            Log.warn("Downloaded plugin is NOT trusted: " + downloaded.getFileName());
        }
    }

    public static void updateProjectLibraries(Project p) throws IOException {
        List<Path> jars = LibraryManager.listJars(p);
        StringBuilder sb = new StringBuilder();
        for (Path j : jars) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(j.toAbsolutePath().toString());
        }
        p.updateLibraries(sb.toString());
    }

    private PackageRepository() {}
}