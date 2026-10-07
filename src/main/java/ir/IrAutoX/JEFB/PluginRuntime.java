package ir.IrAutoX.JEFB;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.tools.JavaCompiler;

public final class PluginRuntime {

    static final String PLUGINS_DIR = JBuild.SETTINGS_DIR + File.separator + "plugins";

    static final List<PluginApi> LOADED = new ArrayList<>();
    static final Map<String, Consumer<List<String>>> CONSOLE_CMDS = new LinkedHashMap<>();
    static final Map<String, Runnable> TOOLBAR_BUTTONS = new LinkedHashMap<>();
    static final Map<String, Map<String, Runnable>> MENU_ITEMS = new LinkedHashMap<>();
    static final List<EditorContextAction> EDITOR_CTX = new ArrayList<>();
    static final List<String> COMPILER_ARGS = new ArrayList<>();
    static final List<Path> EXTRA_CP = new ArrayList<>();
    static final Map<String, Path> EXTRA_JAR_ENTRIES = new LinkedHashMap<>();
    static final List<Consumer<PluginApi.BuildContext>> PRE_HOOKS = new ArrayList<>();
    static final List<Consumer<PluginApi.BuildContext>> POST_HOOKS = new ArrayList<>();
    static final List<Consumer<Diag>> DIAG_HANDLERS = new ArrayList<>();
    static final List<Consumer<Throwable>> ERROR_HANDLERS = new ArrayList<>();
    static final List<String> ANNOTATION_PROCESSORS = new ArrayList<>();
    static JavaCompiler CUSTOM_COMPILER = null;
    static Path SOURCE_DIR = null;
    static Path OUTPUT_DIR = null;

    public static class EditorContextAction {
        public final String label;
        public final Runnable action;
        public EditorContextAction(String label, Runnable action) {
            this.label = label; this.action = action;
        }
    }

    public static List<PluginApi> loaded() { return LOADED; }
    public static Map<String, Consumer<List<String>>> consoleCommands() { return CONSOLE_CMDS; }
    public static List<EditorContextAction> editorContextActions() { return EDITOR_CTX; }
    public static Map<String, Runnable> getToolbarButtons() { return TOOLBAR_BUTTONS; }
    public static Map<String, Map<String, Runnable>> getMenuItems() { return MENU_ITEMS; }
    public static List<String> getCompilerArgs() { return COMPILER_ARGS; }
    public static List<Path> getExtraClasspath() { return EXTRA_CP; }
    public static Map<String, Path> getExtraJarEntries() { return EXTRA_JAR_ENTRIES; }
    public static List<String> getAnnotationProcessors() { return ANNOTATION_PROCESSORS; }
    public static JavaCompiler getCustomCompiler() { return CUSTOM_COMPILER; }
    public static Path getSourceDirectory() { return SOURCE_DIR; }
    public static Path getOutputDirectory() { return OUTPUT_DIR; }

    public static boolean runConsoleCommand(String name, List<String> args) {
        Consumer<List<String>> h = CONSOLE_CMDS.get(name);
        if (h == null) return false;
        try { h.accept(args); return true; }
        catch (Exception e) {
            Log.error("Plugin command failed: " + Log.msg(e));
            fireError(e);
            return true;
        }
    }

    public static void fireBuild() {
        for (PluginApi api : LOADED) {
            try { api.onBuild(new ContextImpl()); }
            catch (Throwable t) { Log.error("onBuild failed: " + Log.msg(t)); fireError(t); }
        }
    }

    public static void fireRun() {
        for (PluginApi api : LOADED) {
            try { api.onRun(new ContextImpl()); }
            catch (Throwable t) { Log.error("onRun failed: " + Log.msg(t)); fireError(t); }
        }
    }

    public static void fireJar() {
        for (PluginApi api : LOADED) {
            try { api.onJar(new ContextImpl()); }
            catch (Throwable t) { Log.error("onJar failed: " + Log.msg(t)); fireError(t); }
        }
    }

    public static void fireEditorChange(Path file, String content) {
        for (PluginApi api : LOADED) {
            try { api.onEditorChange(new ContextImpl(), file, content); }
            catch (Throwable ignored) {}
        }
    }

    public static void fireEditorSave(Path file, String content) {
        for (PluginApi api : LOADED) {
            try { api.onEditorSave(new ContextImpl(), file, content); }
            catch (Throwable ignored) {}
        }
    }

    public static void firePreCompile(Project p, List<String> opts, List<Path> sources) {
        PluginApi.BuildContext ctx = new BuildContextImpl(p, opts, sources);
        for (Consumer<PluginApi.BuildContext> h : PRE_HOOKS) {
            try { h.accept(ctx); } catch (Throwable t) { Log.warn("pre hook: " + Log.msg(t)); }
        }
    }

    public static void firePostCompile(Project p, boolean ok) {
        PluginApi.BuildContext ctx = new BuildContextImpl(p, new ArrayList<>(), new ArrayList<>());
        for (Consumer<PluginApi.BuildContext> h : POST_HOOKS) {
            try { h.accept(ctx); } catch (Throwable t) { Log.warn("post hook: " + Log.msg(t)); }
        }
    }

    public static void fireDiagnostic(Diag d) {
        for (Consumer<Diag> h : DIAG_HANDLERS) {
            try { h.accept(d); } catch (Throwable ignored) {}
        }
    }

    public static void fireError(Throwable t) {
        for (Consumer<Throwable> h : ERROR_HANDLERS) {
            try { h.accept(t); } catch (Throwable ignored) {}
        }
    }

    public static void loadAll() {
        Path dir = Paths.get(PLUGINS_DIR);
        if (!Files.isDirectory(dir)) {
            Log.warn("Plugins directory not found: " + dir);
            return;
        }
        Log.raw("[plugin] Scanning " + dir);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
            int found = 0, loaded = 0;
            for (Path j : ds) {
                found++;
                Log.raw("[plugin] Found: " + j.getFileName());
                TrustedPublisher.VerifyResult vr = TrustedPublisher.verify(j);
                Log.raw("[plugin]   signed=" + vr.signed
                        + " sigValid=" + vr.signatureValid
                        + " keyTrusted=" + vr.keyTrusted
                        + " company=" + vr.company);
                if (vr.isTrusted()) {
                    Log.raw("[plugin]   -> TRUSTED, loading...");
                    load(j);
                    loaded++;
                } else if (vr.signed && vr.signatureValid) {
                    Log.warn("[plugin]   -> Signature valid, but key not trusted.");
                } else if (vr.signed) {
                    Log.warn("[plugin]   -> Signed but INVALID: " + vr.message);
                } else {
                    Log.warn("[plugin]   -> Not signed: " + vr.message);
                }
            }
            Log.raw("[plugin] Scanned " + found + " file(s), loaded " + loaded);
        } catch (IOException e) {
            Log.error("Plugin scan failed: " + Log.msg(e));
        }
    }

    public static void load(Path jar) {
        try {
            Plugin.Info info = Plugin.readInfo(jar);
            if (info == null) { Log.warn("[plugin] Cannot read JEFB.PLUGIN from " + jar.getFileName()); return; }
            if (info.mainClass == null || info.mainClass.isBlank()) {
                Log.warn("[plugin] Plugin has no main class: " + jar.getFileName()); return;
            }
            Log.raw("[plugin] Loading class: " + info.mainClass);
            URL[] urls = new URL[]{ jar.toUri().toURL() };
            ClassLoader loader = new URLClassLoader(urls, PluginRuntime.class.getClassLoader());
            Class<?> cls = Class.forName(info.mainClass, true, loader);
            Log.raw("[plugin] Class loaded: " + cls.getName());
            Object obj = cls.getDeclaredConstructor().newInstance();
            if (!(obj instanceof PluginApi api)) {
                Log.warn("[plugin] Class does not implement PluginApi: " + info.mainClass); return;
            }
            LOADED.add(api);
            api.onLoad(new ContextImpl());
            Log.ok("Loaded plugin: " + api.getName() + " v" + api.getVersion());
        } catch (ClassNotFoundException e) {
            Log.error("[plugin] Class not found: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            Log.error("[plugin] No public no-arg constructor in plugin class");
        } catch (Throwable t) {
            Log.error("[plugin] Load failed for " + jar.getFileName() + ": " + Log.msg(t));
            if (Log.debug) t.printStackTrace();
        }
    }

    public static void unloadAll() {
        for (PluginApi api : LOADED) {
            try { api.onUnload(); } catch (Throwable ignored) {}
        }
        LOADED.clear();
        CONSOLE_CMDS.clear();
        EDITOR_CTX.clear();
        TOOLBAR_BUTTONS.clear();
        MENU_ITEMS.clear();
        COMPILER_ARGS.clear();
        EXTRA_CP.clear();
        EXTRA_JAR_ENTRIES.clear();
        PRE_HOOKS.clear();
        POST_HOOKS.clear();
        DIAG_HANDLERS.clear();
        ERROR_HANDLERS.clear();
        ANNOTATION_PROCESSORS.clear();
        CUSTOM_COMPILER = null;
        SOURCE_DIR = null;
        OUTPUT_DIR = null;
    }

    static class BuildContextImpl implements PluginApi.BuildContext {
        final Project project;
        final List<String> opts;
        final List<Path> sources;
        BuildContextImpl(Project p, List<String> o, List<Path> s) {
            this.project = p; this.opts = o; this.sources = s;
        }
        @Override public Path getProjectRoot() { return project != null ? project.root : null; }
        @Override public Path getClassesDir() { return project != null ? project.classesPath() : null; }
        @Override public List<String> getCompilerArgs() { return opts; }
        @Override public List<Path> getClasspath() { return EXTRA_CP; }
        @Override public String getProjectName() { return project != null ? project.name : ""; }
        @Override public String getProjectVersion() { return project != null ? project.version : ""; }
        @Override public int getJavaRelease() { return project != null ? project.javaRelease : 17; }
    }

    static class ContextImpl implements PluginApi.JEFBContext {

        @Override public void addToolbarButton(String label, String iconName, Runnable onClick) {
            if (label == null || label.isBlank() || onClick == null) return;
            TOOLBAR_BUTTONS.put(label, onClick);
        }

        @Override public void addMenuItem(String menu, String label, Runnable onClick) {
            if (menu == null || menu.isBlank()) return;
            if (label == null || label.isBlank() || onClick == null) return;
            MENU_ITEMS.computeIfAbsent(menu, k -> new LinkedHashMap<>()).put(label, onClick);
        }

        @Override public void addConsoleCommand(String name, Consumer<List<String>> handler) {
            CONSOLE_CMDS.put(name, handler);
        }

        @Override public void addEditorContextMenu(String label, Runnable onClick) {
            EDITOR_CTX.add(new EditorContextAction(label, onClick));
        }

        @Override public void log(String msg) { IDE.appendConsole("[plugin] " + msg); }
        @Override public void logError(String msg) { IDE.appendConsole("[plugin ERROR] " + msg); }
        @Override public void logWarn(String msg) { IDE.appendConsole("[plugin WARN] " + msg); }

        @Override public void clearConsole() {
            if (IDE.console != null) IDE.console.setText("");
        }

        @Override public void appendConsole(String line) { IDE.appendConsole(line); }

        @Override public Path getProjectRoot() { return IDE.projectRoot; }
        @Override public String getProjectName() { return IDE.project != null ? IDE.project.name : "(no project)"; }
        @Override public String getProjectVersion() { return IDE.project != null ? IDE.project.version : "0.0.0"; }
        @Override public Path getSourceRoot() { return IDE.project != null ? IDE.project.sourcePath() : null; }
        @Override public Path getResourcesRoot() { return IDE.project != null ? IDE.project.resourcesPath() : null; }
        @Override public Path getLibRoot() { return IDE.project != null ? IDE.project.root.resolve("lib") : null; }
        @Override public Path getBuildRoot() { return IDE.project != null ? IDE.project.root.resolve("build") : null; }

        @Override public Path getCurrentFilePath() {
            EditorPane ep = IDE.currentEditor();
            return ep == null ? null : ep.file;
        }

        @Override public String getCurrentFileName() {
            Path p = getCurrentFilePath();
            return p == null ? null : p.getFileName().toString();
        }

        @Override public String getCurrentFileContent() {
            EditorPane ep = IDE.currentEditor();
            return ep == null ? "" : ep.text.getText();
        }

        @Override public void setCurrentFileContent(String content) {
            EditorPane ep = IDE.currentEditor();
            if (ep != null) SwingUtilities.invokeLater(() -> { ep.text.setText(content); ep.dirty = true; });
        }

        @Override public void insertAtCursor(String text) {
            EditorPane ep = IDE.currentEditor();
            if (ep != null) SwingUtilities.invokeLater(() -> {
                int pos = ep.text.getCaretPosition();
                ep.text.insert(text, pos);
                ep.text.setCaretPosition(pos + text.length());
                ep.dirty = true;
                ep.text.requestFocusInWindow();
            });
        }

        @Override public void replaceSelection(String text) {
            EditorPane ep = IDE.currentEditor();
            if (ep != null) SwingUtilities.invokeLater(() -> {
                ep.text.replaceSelection(text);
                ep.dirty = true;
                ep.text.requestFocusInWindow();
            });
        }

        @Override public String getSelection() {
            EditorPane ep = IDE.currentEditor();
            if (ep == null) return "";
            String s = ep.text.getSelectedText();
            return s == null ? "" : s;
        }

        @Override public int getCaretPosition() {
            EditorPane ep = IDE.currentEditor();
            return ep == null ? 0 : ep.text.getCaretPosition();
        }

        @Override public void setCaretPosition(int pos) {
            EditorPane ep = IDE.currentEditor();
            if (ep != null) SwingUtilities.invokeLater(() -> ep.text.setCaretPosition(pos));
        }

        @Override public int getLineNumber() {
            EditorPane ep = IDE.currentEditor();
            if (ep == null) return 0;
            try { return ep.text.getLineOfOffset(ep.text.getCaretPosition()) + 1; }
            catch (Exception e) { return 0; }
        }

        @Override public void goToLine(int line) {
            EditorPane ep = IDE.currentEditor();
            if (ep != null) SwingUtilities.invokeLater(() -> {
                try {
                    int off = ep.text.getLineStartOffset(Math.max(0, line - 1));
                    ep.text.setCaretPosition(off);
                    ep.text.requestFocusInWindow();
                } catch (Exception ignored) {}
            });
        }

        @Override public void openFileInEditor(Path path) { SwingUtilities.invokeLater(() -> IDE.openEditor(path)); }
        @Override public void openTextInEditor(String title, String content) { SwingUtilities.invokeLater(() -> IDE.openDecompiled(title, content)); }
        @Override public void closeCurrentTab() { SwingUtilities.invokeLater(EditorActions::closeCurrent); }

        @Override public List<Path> getOpenFiles() {
            List<Path> out = new ArrayList<>();
            if (IDE.editorTabs == null) return out;
            for (int i = 0; i < IDE.editorTabs.getTabCount(); i++) {
                Component c = IDE.editorTabs.getComponentAt(i);
                if (c instanceof EditorPane ep) out.add(ep.file);
            }
            return out;
        }

        @Override public List<Path> listProjectFiles() {
            List<Path> out = new ArrayList<>();
            Path root = getProjectRoot();
            if (root == null || !Files.isDirectory(root)) return out;
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                        String s = f.toString();
                        if (s.contains(File.separator + ".git" + File.separator)) return FileVisitResult.CONTINUE;
                        if (s.contains(File.separator + "build" + File.separator)) return FileVisitResult.CONTINUE;
                        out.add(f);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {}
            return out;
        }

        @Override public List<Path> listJavaFiles() {
            List<Path> out = new ArrayList<>();
            for (Path p : listProjectFiles()) if (p.toString().endsWith(".java")) out.add(p);
            return out;
        }

        @Override public String readFile(Path path) {
            try { return IO.read(path); } catch (IOException e) { return null; }
        }

        @Override public void writeFile(Path path, String content) {
            try { IO.write(path, content); } catch (IOException e) { Log.error("writeFile: " + Log.msg(e)); }
        }

        @Override public boolean fileExists(Path path) { return Files.exists(path); }

        @Override public void deleteFile(Path path) {
            try {
                if (Files.isDirectory(path)) IO.deleteRecursively(path);
                else Files.deleteIfExists(path);
            } catch (IOException e) { Log.error("deleteFile: " + Log.msg(e)); }
        }

        @Override public Path resolveProject(String relative) {
            Path root = getProjectRoot();
            return root == null ? null : root.resolve(relative);
        }

        @Override public boolean runBuild() {
            try {
                if (IDE.project == null) return false;
                Compiler.Result r = new Compiler(IDE.project).compile();
                return r.ok;
            } catch (Exception e) { return false; }
        }

        @Override public boolean runProject() {
            if (IDE.project == null) return false;
            SwingUtilities.invokeLater(IDE::runRun);
            return true;
        }

        @Override public boolean packageJar() {
            if (IDE.project == null) return false;
            SwingUtilities.invokeLater(IDE::runJar);
            return true;
        }

        @Override public void cleanBuild() {
            if (IDE.project == null) return;
            try { IO.deleteRecursively(IDE.project.root.resolve("build")); } catch (IOException ignored) {}
        }

        @Override public void saveCurrentFile() { SwingUtilities.invokeLater(EditorActions::saveCurrent); }
        @Override public void saveAllFiles() { SwingUtilities.invokeLater(EditorActions::saveAll); }
        @Override public void setStatus(String text) { SwingUtilities.invokeLater(() -> IDE.setStatus(text)); }

        @Override public void showMessage(String msg) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    IDE.frame, msg, "Plugin", JOptionPane.INFORMATION_MESSAGE));
        }

        @Override public void showError(String msg) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    IDE.frame, msg, "Plugin Error", JOptionPane.ERROR_MESSAGE));
        }

        @Override public String promptInput(String title, String defaultValue) {
            return JOptionPane.showInputDialog(IDE.frame, title, defaultValue);
        }

        @Override public boolean confirm(String title, String message) {
            int r = JOptionPane.showConfirmDialog(IDE.frame, message, title, JOptionPane.YES_NO_OPTION);
            return r == JOptionPane.YES_OPTION;
        }

        @Override public void setLineHighlight(int line, Color color) {}
        @Override public void clearLineHighlights() {}
        @Override public JFrame getFrame() { return IDE.frame; }
        @Override public JTextArea getConsole() { return IDE.console; }

        @Override public void addCompilerArg(String arg) { if (arg != null && !arg.isBlank()) COMPILER_ARGS.add(arg); }
        @Override public void removeCompilerArg(String arg) { COMPILER_ARGS.remove(arg); }
        @Override public List<String> getCompilerArgs() { return COMPILER_ARGS; }

        @Override public void addClasspathEntry(Path jarOrDir) { if (jarOrDir != null) EXTRA_CP.add(jarOrDir); }
        @Override public void removeClasspathEntry(Path jarOrDir) { EXTRA_CP.remove(jarOrDir); }
        @Override public List<Path> getClasspathEntries() { return EXTRA_CP; }

        @Override public void setSourceDirectory(Path dir) { SOURCE_DIR = dir; }
        @Override public void setOutputDirectory(Path dir) { OUTPUT_DIR = dir; }

        @Override public void addAnnotationProcessor(String processorClass) {
            if (processorClass != null && !processorClass.isBlank()) ANNOTATION_PROCESSORS.add(processorClass);
        }

        @Override public void setProcessorPath(List<Path> paths) {
            for (Path p : paths) { ANNOTATION_PROCESSORS.add(p.toAbsolutePath().toString()); }
        }

        @Override public void setCustomCompiler(JavaCompiler compiler) { CUSTOM_COMPILER = compiler; }

        @Override public void addJarEntry(String entryName, Path sourceFile) {
            if (entryName != null && sourceFile != null) EXTRA_JAR_ENTRIES.put(entryName, sourceFile);
        }

        @Override public void registerPreCompileHook(Consumer<PluginApi.BuildContext> hook) {
            if (hook != null) PRE_HOOKS.add(hook);
        }

        @Override public void registerPostCompileHook(Consumer<PluginApi.BuildContext> hook) {
            if (hook != null) POST_HOOKS.add(hook);
        }

        @Override public void registerDiagnosticHandler(Consumer<Diag> handler) {
            if (handler != null) DIAG_HANDLERS.add(handler);
        }

        @Override public void registerErrorHandler(Consumer<Throwable> handler) {
            if (handler != null) ERROR_HANDLERS.add(handler);
        }
    }

    private PluginRuntime() {}
}