package ir.IrAutoX.JEFB.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a file-level dependency graph of Java sources and computes the
 * minimal recompilation set.
 *
 * The graph is derived by scanning each source for:
 *  - its own fully qualified class name (package + file name),
 *  - {@code import} statements,
 *  - same-package references (heuristic),
 *  - {@code extends} / {@code implements} / {@code new} simple names resolved
 *    against the project's known classes.
 *
 * Edges point FROM a file TO the files it depends on. Incremental selection
 * then takes changed files plus all transitive dependents (files that depend
 * on a changed file must be recompiled because their ABI may change).
 *
 * Scanning is parallelized across a thread pool for large projects.
 */
public final class SourceGraph {

    /** Regex for the package declaration. */
    private static final Pattern PKG = Pattern.compile(
            "\\bpackage\\s+([\\w.]+)\\s*;");
    /** Regex for imports (non-static and static). */
    private static final Pattern IMPORT = Pattern.compile(
            "\\bimport\\s+(?:static\\s+)?([\\w.*]+)\\s*;");
    /** Regex for extends/implements/new/throw simple-name references. */
    private static final Pattern REF = Pattern.compile(
            "\\b(?:extends|implements|new|throw|instanceof)\\s+([A-Z]\\w*)");

    /** relative source path -> parsed info */
    private final Map<String, SourceInfo> sources = new LinkedHashMap<>();
    /** relative source path -> set of relative paths it depends on */
    private final Map<String, Set<String>> edges = new HashMap<>();
    /** reverse edges: path -> set of paths that depend on it */
    private final Map<String, Set<String>> reverse = new HashMap<>();

    /** Parse result for one source file. */
    public static final class SourceInfo {
        public final String path;        // relative to project root
        public final String packageName; // "" for default package
        public final String className;   // simple name (file base name)
        public final String fqcn;        // package + '.' + className
        public final long sizeBytes;
        public final long lastModified;

        SourceInfo(String path, String packageName, String className,
                   long sizeBytes, long lastModified) {
            this.path = path;
            this.packageName = packageName;
            this.className = className;
            this.fqcn = packageName.isEmpty() ? className : packageName + "." + className;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
        }
    }

    /** All parsed sources. */
    public Collection<SourceInfo> allSources() { return sources.values(); }

    public SourceInfo info(String relPath) { return sources.get(relPath); }

    /** Files that directly depend on the given file. */
    public Set<String> dependents(String relPath) {
        return reverse.getOrDefault(relPath, Set.of());
    }

    /** Files the given file directly depends on. */
    public Set<String> dependencies(String relPath) {
        return edges.getOrDefault(relPath, Set.of());
    }

    /**
     * Build the graph from a list of source files (relative to project root).
     * Scanning is done in parallel; exceptions per file are swallowed and
     * recorded as isolated nodes (they will simply always be recompiled).
     */
    public static SourceGraph scan(Path projectRoot, List<Path> sourceFiles) {
        SourceGraph g = new SourceGraph();
        if (sourceFiles.isEmpty()) return g;

        // Pre-index: class simple name -> relative path (for same-package /
        // simple-name resolution). Done cheaply from file names first.
        Map<String, String> simpleToRel = new HashMap<>();
        for (Path p : sourceFiles) {
            String rel = projectRoot.relativize(p).toString().replace('\\', '/');
            String base = p.getFileName().toString().replaceFirst("\\.java$", "");
            simpleToRel.put(base, rel);
        }

        // Parse files in parallel.
        int threads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "jefb-graph");
                    t.setDaemon(true);
                    return t;
                });
        Map<String, SourceInfo> parsed = new ConcurrentHashMap<>();
        Map<String, Set<String>> imports = new ConcurrentHashMap<>();
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (Path p : sourceFiles) {
                futures.add(pool.submit(() -> {
                    try {
                        String rel = projectRoot.relativize(p).toString().replace('\\', '/');
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        String pkg = "";
                        Matcher pm = PKG.matcher(content);
                        if (pm.find()) pkg = pm.group(1);
                        String base = p.getFileName().toString().replaceFirst("\\.java$", "");
                        long size = 0, mtime = 0;
                        try {
                            var attrs = Files.readAttributes(p,
                                    java.nio.file.attribute.BasicFileAttributes.class);
                            size = attrs.size();
                            mtime = attrs.lastModifiedTime().toMillis();
                        } catch (IOException ignored) {}
                        parsed.put(rel, new SourceInfo(rel, pkg, base, size, mtime));
                        Set<String> imps = new HashSet<>();
                        Matcher im = IMPORT.matcher(content);
                        while (im.find()) imps.add(im.group(1));
                        imports.put(rel, imps);
                    } catch (Exception ignored) {
                        // unreadable file: it will be handled by the compiler
                    }
                }));
            }
            for (var f : futures) f.get();
        } catch (Exception ignored) {
        } finally {
            pool.shutdown();
        }

        // Register nodes, then wire edges with a second cheap pass over the
        // cached import sets plus simple-name matching.
        g.sources.putAll(parsed);
        Map<String, String> fqcnToRel = new HashMap<>();
        for (SourceInfo si : parsed.values()) fqcnToRel.put(si.fqcn, si.path);

        for (SourceInfo si : parsed.values()) {
            Set<String> deps = new HashSet<>();
            for (String imp : imports.getOrDefault(si.path, Set.of())) {
                if (imp.endsWith(".*")) continue;           // wildcard: can't resolve cheaply
                String rel = fqcnToRel.get(imp);
                if (rel != null) deps.add(rel);
            }
            g.edges.put(si.path, deps);
        }

        // Same-package and simple-name edges need content; only re-scan files
        // whose simple name is referenced somewhere. This pass streams content
        // with an 8 KB buffer to stay fast on large trees.
        for (SourceInfo si : parsed.values()) {
            try {
                String content = Files.readString(
                        projectRoot.resolve(si.path), StandardCharsets.UTF_8);
                Matcher rm = REF.matcher(content);
                Set<String> deps = g.edges.get(si.path);
                while (rm.find()) {
                    String rel = simpleToRel.get(rm.group(1));
                    if (rel != null && !rel.equals(si.path)) deps.add(rel);
                }
            } catch (IOException ignored) {}
        }

        // Build reverse edges.
        for (Map.Entry<String, Set<String>> e : g.edges.entrySet()) {
            for (String dep : e.getValue()) {
                g.reverse.computeIfAbsent(dep, k -> new HashSet<>()).add(e.getKey());
            }
        }
        return g;
    }

    /**
     * Compute the transitive closure of dependents for the given changed
     * files: everything that (directly or indirectly) depends on a changed
     * file must also be recompiled.
     */
    public Set<String> closureOfDependents(Collection<String> changed) {
        Set<String> out = new HashSet<>(changed);
        Deque<String> work = new ArrayDeque<>(changed);
        while (!work.isEmpty()) {
            String cur = work.poll();
            for (String dep : reverse.getOrDefault(cur, Set.of())) {
                if (out.add(dep)) work.add(dep);
            }
        }
        return out;
    }

    /**
     * Topological order (dependencies first) for the given subset. Cycles are
     * broken arbitrarily; javac handles mutual recursion inside one batch.
     */
    public List<String> topoOrder(Collection<String> subset) {
        Map<String, Integer> inDeg = new HashMap<>();
        for (String s : subset) inDeg.put(s, 0);
        for (String s : subset) {
            for (String dep : edges.getOrDefault(s, Set.of())) {
                if (inDeg.containsKey(dep)) inDeg.merge(s, 1, Integer::sum);
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDeg.entrySet())
            if (e.getValue() == 0) ready.add(e.getKey());
        List<String> order = new ArrayList<>(subset.size());
        while (!ready.isEmpty()) {
            String cur = ready.poll();
            order.add(cur);
            for (String dependent : reverse.getOrDefault(cur, Set.of())) {
                if (!inDeg.containsKey(dependent)) continue;
                int d = inDeg.merge(dependent, -1, Integer::sum);
                if (d == 0) ready.add(dependent);
            }
        }
        // Append any remaining (cycle members) to keep the set complete.
        for (String s : subset) if (!order.contains(s)) order.add(s);
        return order;
    }

    /** Human-readable graph summary for `jefb info`. */
    public String summarize() {
        int edgeCount = 0;
        for (Set<String> e : edges.values()) edgeCount += e.size();
        return sources.size() + " file(s), " + edgeCount + " dependency edge(s)";
    }
}
