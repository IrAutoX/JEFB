package ir.IrAutoX.JEFB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class Cache {

    static final String CACHE_FILE = "build/.jefb-cache";

    public static Map<String, String> load(Project p) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            String s = Crypto.readEncrypted(p.root.resolve(CACHE_FILE));
            if (s == null) return m;
            for (String line : s.split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                m.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (IOException ignored) {}
        return m;
    }

    public static void save(Project p, Map<String, String> m) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# JEFB build cache v4\n");
        List<String> keys = new ArrayList<>(m.keySet());
        Collections.sort(keys);
        for (String k : keys) sb.append(k).append("=").append(m.get(k)).append("\n");
        Crypto.writeEncrypted(p.root.resolve(CACHE_FILE), sb.toString());
    }

    public static String fileHash(Path f) throws IOException {
        return Crypto.sha256(f);
    }

    private Cache() {}
}