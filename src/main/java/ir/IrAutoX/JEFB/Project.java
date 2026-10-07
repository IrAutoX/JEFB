package ir.IrAutoX.JEFB;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class Project {

    static final String CONFIG_FILE  = "jefb.conf";
    static final String CLASSES_DIR  = "build/classes";

    public final Path   root;
    public final String name;
    public final String version;
    public final int    javaRelease;
    public final String mainClass;
    public final String sourceDir;
    public final String resourcesDir;
    public final String libraries;
    public final String signature;
    public final boolean mixins;
    public final String mixinConfig;
    public final String compilerArgs;
    public final String compilerClasspath;
    public final String compilerProcessor;
    public final String customCompiler;
    public final String preBuildScript;
    public final String postBuildScript;

    public Project(Path root, String name, String version, int javaRelease,
                   String mainClass, String sourceDir, String resourcesDir,
                   String libraries, String signature,
                   boolean mixins, String mixinConfig,
                   String compilerArgs, String compilerClasspath,
                   String compilerProcessor, String customCompiler,
                   String preBuildScript, String postBuildScript) {
        this.root = root;
        this.name = name;
        this.version = version;
        this.javaRelease = javaRelease;
        this.mainClass = mainClass;
        this.sourceDir = sourceDir;
        this.resourcesDir = resourcesDir;
        this.libraries = libraries;
        this.signature = signature;
        this.mixins = mixins;
        this.mixinConfig = mixinConfig;
        this.compilerArgs = compilerArgs;
        this.compilerClasspath = compilerClasspath;
        this.compilerProcessor = compilerProcessor;
        this.customCompiler = customCompiler;
        this.preBuildScript = preBuildScript;
        this.postBuildScript = postBuildScript;
    }

    public static Project load(Path root) throws BuildException {
        Path cfg = root.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(cfg))
            throw new BuildException("Not a JEF Builder project (missing " + CONFIG_FILE + ").");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new BuildException("Cannot read " + CONFIG_FILE + ": " + Log.msg(e));
        }
        String name = p.getProperty("name",
                root.getFileName() == null ? "app" : root.getFileName().toString()).trim();
        String ver  = p.getProperty("version", "1.0.0").trim();
        String js   = p.getProperty("java", "17").trim();
        String main = p.getProperty("main", "").trim();
        String src  = p.getProperty("source", "src/main/java").trim();
        String res  = p.getProperty("resources", "src/main/resources").trim();
        String libs = p.getProperty("libraries", "").trim();
        String sig  = p.getProperty("signature", "").trim();
        String mix  = p.getProperty("mixins", "false").trim();
        String mixCfg = p.getProperty("mixinConfig", "mixins.json").trim();
        String cArgs = p.getProperty("compiler.args", "").trim();
        String cCp   = p.getProperty("compiler.classpath", "").trim();
        String cProc = p.getProperty("compiler.processor", "").trim();
        String cComp = p.getProperty("compiler.custom", "").trim();
        String cPre  = p.getProperty("compiler.preBuild", "").trim();
        String cPost = p.getProperty("compiler.postBuild", "").trim();
        int jr;
        try { jr = Integer.parseInt(js); }
        catch (NumberFormatException e) {
            throw new BuildException("Invalid java= value: \"" + js + "\"");
        }
        boolean mixB = "true".equalsIgnoreCase(mix);
        return new Project(root, name, ver, jr, main, src, res, libs, sig,
                mixB, mixCfg, cArgs, cCp, cProc, cComp, cPre, cPost);
    }

    public Path sourcePath()    { return root.resolve(sourceDir); }
    public Path resourcesPath() { return root.resolve(resourcesDir); }
    public Path classesPath()   { return root.resolve(CLASSES_DIR); }
    public String libraries()   { return libraries; }
    public boolean mixins()     { return mixins; }
    public String mixinConfig() { return mixinConfig; }
    public String compilerArgs()      { return compilerArgs; }
    public String compilerClasspath() { return compilerClasspath; }
    public String compilerProcessor() { return compilerProcessor; }
    public String customCompiler()    { return customCompiler; }
    public String preBuildScript()    { return preBuildScript; }
    public String postBuildScript()   { return postBuildScript; }

    public void updateLibraries(String value) throws IOException {
        updateConfig("libraries", value == null ? "" : value);
    }

    public void updateSignature(String value) throws IOException {
        updateConfig("signature", value == null ? "" : value);
    }

    public void updateVersion(String value) throws IOException {
        updateConfig("version", value == null ? "1.0.0" : value);
    }

    public void updateMain(String value) throws IOException {
        updateConfig("main", value == null ? "" : value);
    }

    public void updateMixins(boolean value) throws IOException {
        updateConfig("mixins", String.valueOf(value));
    }

    public void updateMixinConfig(String value) throws IOException {
        updateConfig("mixinConfig", value == null ? "mixins.json" : value);
    }

    public void updateCompilerArgs(String value) throws IOException {
        updateConfig("compiler.args", value == null ? "" : value);
    }

    public void updateCompilerClasspath(String value) throws IOException {
        updateConfig("compiler.classpath", value == null ? "" : value);
    }

    public void updateCompilerProcessor(String value) throws IOException {
        updateConfig("compiler.processor", value == null ? "" : value);
    }

    public void updateCustomCompiler(String value) throws IOException {
        updateConfig("compiler.custom", value == null ? "" : value);
    }

    public void updateConfig(String key, String value) throws IOException {
        Path cfg = root.resolve(CONFIG_FILE);
        Properties p = new Properties();
        if (Files.isRegularFile(cfg)) {
            try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                p.load(r);
            }
        }
        p.setProperty(key, value);
        try (Writer w = Files.newBufferedWriter(cfg, StandardCharsets.UTF_8)) {
            p.store(w, null);
        }
    }
}