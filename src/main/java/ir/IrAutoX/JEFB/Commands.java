package ir.IrAutoX.JEFB;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.jar.*;

public final class Commands {

    public static Path resolveRoot() {
        if (IDE.projectRoot != null) return IDE.projectRoot;
        return Paths.get(".").toAbsolutePath().normalize();
    }

    public static int init(List<String> args) {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        if (!args.isEmpty()) root = root.resolve(args.get(0)).normalize();
        try { Files.createDirectories(root); }
        catch (IOException e) { Log.error("Cannot create project dir: " + root); return 2; }
        Path cfg = root.resolve(Project.CONFIG_FILE);
        if (Files.exists(cfg)) { Log.error("Project already initialized."); return 2; }
        String pname = root.getFileName() == null ? "app" : root.getFileName().toString();
        String body =
                "name=" + pname + "\n" +
                "version=1.0.0\n" +
                "java=17\n" +
                "main=Main\n" +
                "source=src/main/java\n" +
                "resources=src/main/resources\n" +
                "libraries=\n" +
                "signature=\n" +
                "mixins=false\n" +
                "mixinConfig=mixins.json\n" +
                "compiler.args=\n" +
                "compiler.classpath=\n" +
                "compiler.processor=\n" +
                "compiler.custom=\n";
        try {
            IO.write(cfg, body);
            Files.createDirectories(root.resolve("src/main/java"));
            Files.createDirectories(root.resolve("src/main/resources"));
            Files.createDirectories(root.resolve("lib"));
            Files.createDirectories(root.resolve("icons"));
            Files.createDirectories(root.resolve("build"));
        } catch (IOException e) { Log.error("Failed to init: " + Log.msg(e)); return 2; }
        Log.ok("Project initialized: " + pname);
        return 0;
    }

    public static int build(List<String> args) {
        Path root = resolveRoot();
        Project p;
        try { p = Project.load(root); }
        catch (BuildException e) { Log.error(e.getMessage()); return 1; }
        return buildFor(p);
    }

    public static int buildFor(Project p) {
        Instant t0 = Instant.now();
        Log.raw("");
        Log.raw(JBuild.NAME + " " + JBuild.VERSION);
        Log.raw("Project: " + p.name);
        Log.raw("");
        Compiler.Result r;
        try { r = new Compiler(p).compile(); }
        catch (BuildException e) {
            Log.error(e.getMessage());
            if (Log.debug) e.printStackTrace();
            return 1;
        } catch (Throwable t) {
            Log.error("Compiler crash: " + Log.msg(t));
            t.printStackTrace();
            return 1;
        }
        if (!r.ok) { printDiags(r.diags, p.root); return 1; }
        Log.raw("");
        Log.ok("BUILD SUCCESS  compiled=" + r.compiled + " cached=" + r.skipped
                + "  (" + Duration.between(t0, Instant.now()).toMillis() + "ms)");
        return 0;
    }

    public static int clean(List<String> args) {
        Path root = resolveRoot();
        Project p;
        try { p = Project.load(root); }
        catch (BuildException e) { Log.error(e.getMessage()); return 1; }
        try { IO.deleteRecursively(p.root.resolve("build")); }
        catch (IOException e) { Log.error("Clean failed: " + Log.msg(e)); return 1; }
        Log.ok("Cleaned.");
        return 0;
    }

    public static int run(List<String> args) {
        Path root = resolveRoot();
        Project p;
        try { p = Project.load(root); }
        catch (BuildException e) { Log.error(e.getMessage()); return 1; }
        if (p.mainClass == null || p.mainClass.isBlank()) {
            Log.error("No main class configured.");
            return 1;
        }
        int b = buildFor(p);
        if (b != 0) return b;
        List<String> pass = new ArrayList<>(args);
        if (!pass.isEmpty() && pass.get(0).equals("--")) pass.remove(0);
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe());
        cmd.add("-cp");
        try {
            cmd.add(joinCp(p.classesPath().toString(),
                    LibraryManager.buildClasspath(p),
                    LibraryManager.bundledClasspath()));
        } catch (IOException e) { cmd.add(p.classesPath().toString()); }
        cmd.add(p.mainClass);
        cmd.addAll(pass);
        Log.raw("");
        Log.raw("--- Run " + p.mainClass + " ---");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process proc = pb.start();
            int code = proc.waitFor();
            Log.raw("--- exit " + code + " ---");
            return code;
        } catch (IOException e) { Log.error("Run failed: " + Log.msg(e)); return 1; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return 1; }
    }

    public static String joinCp(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s == null || s.isBlank()) continue;
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(s);
        }
        return sb.toString();
    }

    public static int sign(List<String> args) {
        Path root = resolveRoot();
        Project p;
        try { p = Project.load(root); }
        catch (BuildException e) { Log.error(e.getMessage()); return 1; }
        int b = buildFor(p);
        if (b != 0) return b;
        try {
            SignMeta meta = SignUtil.loadMeta(p);
            if (meta == null || meta.company == null || meta.company.isBlank()) {
                Log.error("No sign metadata. Configure company/author first.");
                return 1;
            }
            String pHash = SignUtil.projectHash(p);
            meta = new SignMeta(meta.company, meta.author, meta.issued, meta.expires,
                    meta.license, "", pHash);
            String sig = SignUtil.buildSignature(meta);
            meta = new SignMeta(meta.company, meta.author, meta.issued, meta.expires,
                    meta.license, sig, pHash);
            SignUtil.saveMeta(p, meta);
            Map<String, String> sigs = new LinkedHashMap<>();
            Path classes = p.classesPath();
            if (Files.isDirectory(classes)) {
                Files.walkFileTree(classes, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(
                            Path f, java.nio.file.attribute.BasicFileAttributes a) {
                        try { sigs.put(IO.rel(classes, f), Crypto.sha256(f)); }
                        catch (IOException ignored) {}
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            SignUtil.saveSignatures(p, sigs);
            p.updateSignature(sig);
            Log.ok("Signed " + sigs.size() + " class file(s). Company=" + meta.company);
            return 0;
        } catch (Exception e) {
            Log.error("Sign failed: " + Log.msg(e));
            return 1;
        }
    }

    public static int verify(List<String> args) {
        Path root = resolveRoot();
        Project p;
        try { p = Project.load(root); }
        catch (BuildException e) { Log.error(e.getMessage()); return 1; }
        try {
            Path jar = p.root.resolve("build/" + p.name + ".jar");
            if (Files.isRegularFile(jar)) {
                TrustedPublisher.VerifyResult r = TrustedPublisher.verify(jar);
                if (r.isTrusted()) { Log.ok("JAR signature OK. Publisher: " + r.company); return 0; }
                if (r.signed && r.signatureValid) {
                    Log.warn("Signature valid but publisher NOT trusted: " + r.company);
                    return 1;
                }
                Log.error("JAR signature mismatch, missing, or expired.");
                return 1;
            }
            if (SignUtil.verify(p)) { Log.ok("Signature OK."); return 0; }
            Log.error("Signature mismatch, missing, or expired.");
            return 1;
        } catch (Exception e) {
            Log.error("Verify failed: " + Log.msg(e));
            return 1;
        }
    }

    public static int jar(List<String> args) {
        Path root = resolveRoot();
        Project p;
        try { p = Project.load(root); }
        catch (BuildException e) { Log.error(e.getMessage()); return 1; }
        int b = buildFor(p);
        if (b != 0) { Log.error("Build step failed."); return b; }

        SignMeta meta = null;
        try { meta = SignUtil.loadMeta(p); } catch (IOException ignored) {}
        if (meta == null) {
            Log.error("No sign metadata. Configure company/author first.");
            return 1;
        }

        Path classes = p.classesPath();
        Path out = p.root.resolve("build/" + p.name + ".jar");
        Log.raw("[jar] output = " + out.toAbsolutePath());

        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Created-By", JBuild.NAME + " " + JBuild.VERSION);
        mf.getMainAttributes().putValue("JEFB-Company", meta.company);
        mf.getMainAttributes().putValue("JEFB-Author", meta.author);
        mf.getMainAttributes().putValue("JEFB-Issued", meta.issued);
        mf.getMainAttributes().putValue("JEFB-Expires", meta.expires);
        mf.getMainAttributes().putValue("JEFB-Project", p.name);
        mf.getMainAttributes().putValue("JEFB-Version", p.version);
        if (p.mainClass != null && !p.mainClass.isBlank())
            mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, p.mainClass);

        try {
            Files.createDirectories(out.getParent());
            if (Files.exists(out)) {
                try { Files.delete(out); }
                catch (IOException ex) {
                    Log.error("Cannot delete old JAR: " + Log.msg(ex));
                    return 1;
                }
            }

            Set<String> added = new HashSet<>();
            int[] count = {0};
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(out), mf)) {
                if (Files.isDirectory(classes)) {
                    Files.walkFileTree(classes, new SimpleFileVisitor<>() {
                        @Override public FileVisitResult visitFile(
                                Path f, java.nio.file.attribute.BasicFileAttributes a) {
                            try {
                                String e = classes.relativize(f).toString().replace(File.separatorChar, '/');
                                if (added.add(e)) {
                                    jos.putNextEntry(new JarEntry(e));
                                    Files.copy(f, jos);
                                    jos.closeEntry();
                                    count[0]++;
                                }
                            } catch (IOException ex) { Log.warn("Skip " + f + ": " + Log.msg(ex)); }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                for (Path libJar : LibraryManager.listJars(p)) {
                    try (JarFile jf = new JarFile(libJar.toFile())) {
                        Enumeration<JarEntry> en = jf.entries();
                        while (en.hasMoreElements()) {
                            JarEntry le = en.nextElement();
                            if (le.isDirectory()) continue;
                            String name = le.getName();
                            if (name.equals("META-INF/MANIFEST.MF")) continue;
                            if (name.startsWith("META-INF/") && (name.endsWith(".SF")
                                    || name.endsWith(".RSA") || name.endsWith(".DSA"))) continue;
                            if (name.equals("module-info.class")) continue;
                            if (!added.add(name)) continue;
                            try (InputStream is = jf.getInputStream(le)) {
                                jos.putNextEntry(new JarEntry(name));
                                is.transferTo(jos);
                                jos.closeEntry();
                                count[0]++;
                            } catch (IOException ignored) {}
                        }
                    } catch (IOException ignored) {}
                }
                Path iconsDir = p.root.resolve("icons");
                if (Files.isDirectory(iconsDir)) {
                    Files.walkFileTree(iconsDir, new SimpleFileVisitor<>() {
                        @Override public FileVisitResult visitFile(
                                Path f, java.nio.file.attribute.BasicFileAttributes a) {
                            try {
                                String e = "icons/" + f.getFileName();
                                if (added.add(e)) {
                                    jos.putNextEntry(new JarEntry(e));
                                    Files.copy(f, jos);
                                    jos.closeEntry();
                                    count[0]++;
                                }
                            } catch (IOException ignored) {}
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                Path resDir = p.resourcesPath();
                if (Files.isDirectory(resDir)) {
                    Files.walkFileTree(resDir, new SimpleFileVisitor<>() {
                        @Override public FileVisitResult visitFile(
                                Path f, java.nio.file.attribute.BasicFileAttributes a) {
                            try {
                                String e = resDir.relativize(f).toString().replace(File.separatorChar, '/');
                                if (added.add(e)) {
                                    jos.putNextEntry(new JarEntry(e));
                                    Files.copy(f, jos);
                                    jos.closeEntry();
                                    count[0]++;
                                }
                            } catch (IOException ignored) {}
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                for (Map.Entry<String, Path> e : PluginRuntime.getExtraJarEntries().entrySet()) {
                    String name = e.getKey();
                    Path src = e.getValue();
                    if (!Files.isRegularFile(src)) continue;
                    if (!added.add(name)) continue;
                    try {
                        jos.putNextEntry(new JarEntry(name));
                        Files.copy(src, jos);
                        jos.closeEntry();
                        count[0]++;
                        Log.raw("  [jar] + " + name);
                    } catch (IOException ex) { Log.warn("Skip extra " + name + ": " + Log.msg(ex)); }
                }
                if (meta != null) {
                    String mn = "META-INF/JEFB.META";
                    if (added.add(mn)) {
                        jos.putNextEntry(new JarEntry(mn));
                        jos.write(meta.serialize().getBytes(StandardCharsets.UTF_8));
                        jos.closeEntry();
                        count[0]++;
                    }
                }
            }
            Log.ok("Fat JAR created: " + out.toAbsolutePath());
            Log.raw("  Entries: " + count[0]);
            Log.raw("  Size: " + Files.size(out) + " bytes");
            return 0;
        } catch (IOException e) {
            Log.error("Jar failed: " + Log.msg(e));
            return 1;
        }
    }

    public static int info(List<String> args) {
        Path root = resolveRoot();
        Project p;
        try { p = Project.load(root); }
        catch (BuildException e) { Log.error(e.getMessage()); return 1; }
        Log.raw(JBuild.NAME + " " + JBuild.VERSION);
        Log.raw("Author   : " + JBuild.AUTHOR);
        Log.raw("Developer: " + JBuild.DEVELOPER);
        Log.raw("");
        Log.raw("Project  : " + p.name);
        Log.raw("Version  : " + p.version);
        Log.raw("Root     : " + p.root);
        Log.raw("Java     : " + p.javaRelease);
        Log.raw("Main     : " + (p.mainClass.isBlank() ? "(not set)" : p.mainClass));
        Log.raw("Source   : " + p.sourceDir);
        Log.raw("Resources: " + p.resourcesDir);
        Log.raw("Libraries: " + (p.libraries().isBlank() ? "(none)" : p.libraries()));
        Log.raw("Mixins   : " + p.mixins());
        return 0;
    }

    public static int ide(List<String> args) {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        Project p = null;
        try { p = Project.load(root); } catch (BuildException ignored) {}
        if (p != null) IDE.rememberRecent(p.root);
        PluginRuntime.loadAll();
        IDE.launch(p);
        return 0;
    }

    public static void printDiags(List<Diag> diags, Path root) {
        int errs = 0, warns = 0;
        for (Diag d : diags) {
            if (d.sev == Diag.Sev.ERROR) errs++;
            if (d.sev == Diag.Sev.WARNING) warns++;
        }
        for (Diag d : diags) {
            Log.raw("");
            Log.raw("  " + d.file + ":" + d.line + ":" + d.col);
            Log.raw("  " + d.sev.name().toLowerCase() + ": " + d.msg);
            try {
                Path f = root.resolve(d.file);
                if (Files.isRegularFile(f)) {
                    List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                    if (d.line > 0 && d.line <= lines.size())
                        Log.raw("      " + lines.get((int) d.line - 1));
                }
            } catch (IOException ignored) {}
        }
        if (errs > 0) Log.error("Build failed: " + errs + " error(s), " + warns + " warning(s).");
        else if (warns > 0) Log.warn(warns + " warning(s).");
    }

    public static String javaExe() {
        String home = System.getProperty("java.home");
        if (home == null) return "java";
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path exe = Paths.get(home, "bin", win ? "java.exe" : "java");
        return Files.exists(exe) ? exe.toString() : "java";
    }

    private Commands() {}
}