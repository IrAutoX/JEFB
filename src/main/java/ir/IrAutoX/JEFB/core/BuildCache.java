package ir.IrAutoX.JEFB.core;

import ir.IrAutoX.JEFB.Cache;
import ir.IrAutoX.JEFB.Crypto;
import ir.IrAutoX.JEFB.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Content-hash build cache with a classpath fingerprint.
 *
 * Extends the legacy flat cache (source file -> SHA-256) with:
 *  - a fingerprint of the effective compile classpath, so swapping a JAR in
 *    lib/ invalidates every entry that could see it;
 *  - a compiler-args fingerprint, so flag changes trigger recompiles.
 *
 * Format stays line-based "key=value" and sorted on save for reproducibility.
 */
public final class BuildCache {

    private static final String KEY_CP_FINGERPRINT = "@classpath-fingerprint";
    private static final String KEY_ARGS_FINGERPRINT = "@compiler-args-fingerprint";

    private final Map<String, String> backing;
    private final Project project;

    private BuildCache(Project project, Map<String, String> backing) {
        this.project = project;
        this.backing = backing;
    }

    public static BuildCache load(Project project) {
        return new BuildCache(project, Cache.load(project));
    }

    /** Fingerprint of every JAR on the classpath (path + size + mtime + content sample). */
    public static String classpathFingerprint(List<Path> classpath) {
        List<String> parts = new ArrayList<>();
        for (Path p : classpath) {
            try {
                if (!Files.isRegularFile(p) && !Files.isDirectory(p)) {
                    parts.add(p + ":missing");
                    continue;
                }
                long size = Files.isRegularFile(p) ? Files.size(p) : -1;
                long mtime = Files.getLastModifiedTime(p).toMillis();
                String hash = Files.isRegularFile(p) ? Crypto.sha256(p) : "dir";
                parts.add(p.getFileName() + ":" + size + ":" + mtime + ":" + hash);
            } catch (IOException e) {
                parts.add(p + ":unreadable");
            }
        }
        Collections.sort(parts);
        return Crypto.sha256String(String.join("\n", parts));
    }

    /** True when the stored classpath fingerprint matches the current one. */
    public boolean classpathMatches(String fingerprint) {
        String stored = backing.get(KEY_CP_FINGERPRINT);
        return stored == null || stored.equals(fingerprint);
    }

    public boolean argsMatch(String fingerprint) {
        String stored = backing.get(KEY_ARGS_FINGERPRINT);
        return stored == null || stored.equals(fingerprint);
    }

    /** Stored hash for a source file, or null. */
    public String hashOf(String relPath) {
        return backing.get(relPath);
    }

    public void putHash(String relPath, String hash) {
        backing.put(relPath, hash);
    }

    public void storeFingerprints(String cp, String args) {
        if (cp != null) backing.put(KEY_CP_FINGERPRINT, cp);
        if (args != null) backing.put(KEY_ARGS_FINGERPRINT, args);
    }

    /** Remove every per-file entry (full rebuild), keeping fingerprints. */
    public void clearFiles() {
        backing.keySet().removeIf(k -> !k.startsWith("@"));
    }

    /** Remove entries for files that no longer exist in the source set. */
    public void prune(java.util.Set<String> validRelPaths) {
        backing.keySet().removeIf(k ->
                !k.startsWith("@") && !validRelPaths.contains(k));
    }

    public void save() {
        try {
            Cache.save(project, backing);
        } catch (IOException e) {
            ir.IrAutoX.JEFB.Log.warn("Cache save failed: " + ir.IrAutoX.JEFB.Log.msg(e));
        }
    }

    /** Number of per-file entries. */
    public int size() {
        return (int) backing.keySet().stream().filter(k -> !k.startsWith("@")).count();
    }
}
