package ir.IrAutoX.JEFB;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class Compiler {

    public static class Result {
        public final boolean ok;
        public final List<Diag> diags;
        public final int compiled;
        public final int skipped;
        public final long timeMs;
        public Result(boolean ok, List<Diag> d, int c, int s, long t) {
            this.ok = ok; this.diags = d; this.compiled = c; this.skipped = s; this.timeMs = t;
        }
    }

    final Project project;
    public Compiler(Project p) { this.project = p; }

    public Result compile() throws BuildException {
        long t0 = System.currentTimeMillis();

        JavaCompiler javac = PluginRuntime.getCustomCompiler();
        if (javac == null) javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null)
            throw new BuildException("No system Java compiler found. Install a JDK (17+).");

        Path srcDir = project.sourcePath();
        Path customSrc = PluginRuntime.getSourceDirectory();
        if (customSrc != null) srcDir = customSrc;

        List<Path> sources;
        try { sources = IO.listJava(srcDir); }
        catch (IOException e) { throw new BuildException("Scan failed: " + Log.msg(e)); }

        Path classesDir = project.classesPath();
        Path customOut = PluginRuntime.getOutputDirectory();
        if (customOut != null) classesDir = customOut;
        try { Files.createDirectories(classesDir); }
        catch (IOException e) { throw new BuildException("Cannot create classes: " + Log.msg(e)); }

        if (sources.isEmpty()) {
            Log.raw("[1/3] No Java sources found.");
            return new Result(true, Collections.emptyList(), 0, 0,
                    System.currentTimeMillis() - t0);
        }

        Map<String, String> cache = Cache.load(project);
        List<Path> toCompile = new ArrayList<>();
        int skipped = 0;
        for (Path src : sources) {
            try {
                String rel = IO.rel(project.root, src);
                String h = Cache.fileHash(src);
                String prev = cache.get(rel);
                Path outClass = classesDir.resolve(
                        IO.rel(srcDir, src).replaceAll("\\.java$", ".class"));
                if (prev != null && prev.equals(h) && Files.isRegularFile(outClass)) skipped++;
                else toCompile.add(src);
            } catch (IOException e) { toCompile.add(src); }
        }

        Log.raw("[1/3] Sources: " + sources.size() + "  to compile: " + toCompile.size()
                + "  cached: " + skipped);

        if (toCompile.isEmpty()) {
            Log.raw("[2/3] Nothing to compile.");
            applyResources(classesDir);
            return new Result(true, Collections.emptyList(), 0, skipped,
                    System.currentTimeMillis() - t0);
        }

        DiagnosticCollector<JavaFileObject> col = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = javac.getStandardFileManager(
                col, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(toCompile);
            List<String> opts = new ArrayList<>();
            opts.add("-d"); opts.add(classesDir.toString());
            opts.add("-encoding"); opts.add("UTF-8");
            opts.add("-g");
            opts.add("--release"); opts.add(String.valueOf(project.javaRelease));

            try {
                String pc = LibraryManager.buildClasspath(project);
                String bc = LibraryManager.bundledClasspath();
                StringBuilder full = new StringBuilder();
                if (!pc.isBlank()) full.append(pc);
                if (!bc.isBlank()) {
                    if (full.length() > 0) full.append(File.pathSeparator);
                    full.append(bc);
                }
                for (Path extra : PluginRuntime.getExtraClasspath()) {
                    if (full.length() > 0) full.append(File.pathSeparator);
                    full.append(extra.toAbsolutePath().toString());
                }
                String cfgCp = project.compilerClasspath();
                if (cfgCp != null && !cfgCp.isBlank()) {
                    for (String s : cfgCp.split(File.pathSeparator)) {
                        if (s.isBlank()) continue;
                        if (full.length() > 0) full.append(File.pathSeparator);
                        full.append(s.trim());
                    }
                }
                if (full.length() > 0) {
                    opts.add("-classpath");
                    opts.add(full.toString());
                }
            } catch (IOException ignored) {}

            String cfgArgs = project.compilerArgs();
            if (cfgArgs != null && !cfgArgs.isBlank()) {
                for (String a : cfgArgs.split("\\s+")) {
                    if (!a.isBlank()) opts.add(a);
                }
            }

            for (String a : PluginRuntime.getCompilerArgs()) {
                opts.add(a);
            }

            if (project.mixins()) {
                opts.add("-processor");
                String proc = project.compilerProcessor();
                if (proc == null || proc.isBlank())
                    proc = "org.spongepowered.mixin.MixinAnnotationProcessor";
                opts.add(proc);
            } else {
                for (String p : PluginRuntime.getAnnotationProcessors()) {
                    opts.add("-processor"); opts.add(p);
                }
                if (PluginRuntime.getAnnotationProcessors().isEmpty()
                        && (project.compilerProcessor() == null
                        || project.compilerProcessor().isBlank())) {
                    opts.add("-proc:none");
                }
            }

            String cfgProc = project.compilerProcessor();
            if (cfgProc != null && !cfgProc.isBlank() && !project.mixins()) {
                opts.add("-processor");
                opts.add(cfgProc);
            }

            Log.raw("       args: " + opts);

            try {
                PluginRuntime.firePreCompile(project, opts, toCompile);
            } catch (Throwable t) {
                Log.warn("pre-compile hook failed: " + Log.msg(t));
            }

            JavaCompiler.CompilationTask task = javac.getTask(
                    null, fm, col, opts, null, units);
            boolean ok = Boolean.TRUE.equals(task.call());
            List<Diag> diags = new ArrayList<>();
            for (var d : col.getDiagnostics()) {
                String file = "<unknown>";
                if (d.getSource() != null) {
                    try { file = IO.rel(project.root, Paths.get(d.getSource().toUri())); }
                    catch (Exception ignored) { file = d.getSource().getName(); }
                }
                Diag.Sev sev = switch (d.getKind()) {
                    case ERROR -> Diag.Sev.ERROR;
                    case WARNING, MANDATORY_WARNING -> Diag.Sev.WARNING;
                    default -> Diag.Sev.INFO;
                };
                String msg = explainDiagnostic(d.getMessage(null));
                Diag diag = new Diag(sev, file, d.getLineNumber(), d.getColumnNumber(), msg);
                diags.add(diag);
                try { PluginRuntime.fireDiagnostic(diag); }
                catch (Throwable ignored) {}
            }

            if (ok) {
                for (Path src : toCompile) {
                    try { cache.put(IO.rel(project.root, src), Cache.fileHash(src)); }
                    catch (IOException ignored) {}
                }
                try { Cache.save(project, cache); }
                catch (IOException e) { Log.warn("Cache save failed: " + Log.msg(e)); }
            }

            try {
                PluginRuntime.firePostCompile(project, ok);
            } catch (Throwable t) {
                Log.warn("post-compile hook failed: " + Log.msg(t));
            }

            return new Result(ok, diags, toCompile.size(), skipped,
                    System.currentTimeMillis() - t0);
        } catch (IOException e) {
            throw new BuildException("Compiler I/O: " + Log.msg(e));
        } finally {
            applyResources(classesDir);
        }
    }

    void applyResources(Path classesDir) {
        try { IO.copyTree(project.resourcesPath(), classesDir); }
        catch (IOException ignored) {}
    }

    static String explainDiagnostic(String msg) {
        if (msg == null) return "Unknown diagnostic";
        String lower = msg.toLowerCase();
        StringBuilder sb = new StringBuilder(msg);
        if (lower.contains("cannot find symbol"))
            sb.append("\n  -> variable, method, or class name wrong or not imported.");
        else if (lower.contains("incompatible types"))
            sb.append("\n  -> incompatible data types.");
        else if (lower.contains("package") && lower.contains("does not exist"))
            sb.append("\n  -> package not found in classpath.");
        else if (lower.contains("unreported exception"))
            sb.append("\n  -> unhandled exception. Add try-catch.");
        else if (lower.contains("missing return statement"))
            sb.append("\n  -> method must return on all paths.");
        else if (lower.contains("unclosed string literal"))
            sb.append("\n  -> unclosed quotation mark.");
        else if (lower.contains("reached end of file while parsing"))
            sb.append("\n  -> unclosed brace.");
        else if (lower.contains("; expected"))
            sb.append("\n  -> missing semicolon.");
        else if (lower.contains("deprecation"))
            sb.append("\n  -> deprecated API usage.");
        else if (lower.contains("unchecked"))
            sb.append("\n  -> unchecked cast (Generic).");
        return sb.toString();
    }
}