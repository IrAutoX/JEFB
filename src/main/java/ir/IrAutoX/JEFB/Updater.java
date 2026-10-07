package ir.IrAutoX.JEFB;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

public final class Updater {

    static final String UPDATES_URL = "https://api.github.com/repos/IrAutoX/JEFB/releases/latest";

    public static class Release {
        public final String tag;
        public final String name;
        public final String body;
        public final String downloadUrl;
        public final long size;

        public Release(String tag, String name, String body, String downloadUrl, long size) {
            this.tag = tag; this.name = name; this.body = body;
            this.downloadUrl = downloadUrl; this.size = size;
        }
    }

    public static int checkNow() {
        try {
            Release r = fetchLatest();
            if (r == null) { Log.error("Failed to fetch release info."); return 1; }
            Log.raw("Latest: " + r.tag + " - " + r.name);
            Log.raw("Current: " + JBuild.VERSION);
            if (r.tag.replaceAll("[^0-9.]", "").equals(JBuild.VERSION)) {
                Log.ok("Up to date.");
                return 0;
            }
            Log.raw("");
            Log.raw("Release notes:");
            Log.raw(r.body);
            return 0;
        } catch (Exception e) {
            Log.error("Update check failed: " + Log.msg(e));
            return 1;
        }
    }

    public static Release fetchLatest() {
        try {
            HttpClient c = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(UPDATES_URL))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "JEFB-Updater")
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            String json = resp.body();
            String tag = extract(json, "tag_name");
            String name = extract(json, "name");
            String body = extract(json, "body");
            String url = extract(json, "browser_download_url");
            long size = 0;
            int sIdx = json.indexOf("\"size\"");
            if (sIdx > 0) {
                String s = json.substring(sIdx + 6).trim();
                StringBuilder sb = new StringBuilder();
                for (char ch : s.toCharArray()) {
                    if (Character.isDigit(ch)) sb.append(ch);
                    else if (sb.length() > 0) break;
                }
                try { size = Long.parseLong(sb.toString()); } catch (Exception ignored) {}
            }
            return new Release(
                    tag == null ? "" : tag,
                    name == null ? "" : name,
                    body == null ? "" : body.replace("\\n", "\n").replace("\\r", ""),
                    url, size);
        } catch (Exception e) { return null; }
    }

    static String extract(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = q1 + 1;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (q2 < json.length()) {
            char c = json.charAt(q2);
            if (esc) { sb.append(c); esc = false; }
            else if (c == '\\') esc = true;
            else if (c == '"') break;
            else sb.append(c);
            q2++;
        }
        return sb.toString();
    }

    public static void download(Release r, Path dest) throws IOException {
        if (r.downloadUrl == null) throw new IOException("No download URL");
        HttpClient c = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(r.downloadUrl))
                .timeout(Duration.ofMinutes(5)).GET().build();
        try {
            HttpResponse<Path> resp = c.send(req, HttpResponse.BodyHandlers.ofFile(dest));
            if (resp.statusCode() != 200)
                throw new IOException("HTTP " + resp.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    private Updater() {}
}