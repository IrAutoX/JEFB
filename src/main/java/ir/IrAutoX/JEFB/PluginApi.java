package ir.IrAutoX.JEFB;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.tools.JavaCompiler;

public interface PluginApi {

    String getName();
    String getVersion();
    String getAuthor();

    default void onLoad(JEFBContext ctx) {}
    default void onUnload() {}
    default void onBuild(JEFBContext ctx) {}
    default void onRun(JEFBContext ctx) {}
    default void onJar(JEFBContext ctx) {}
    default void onEditorChange(JEFBContext ctx, Path file, String content) {}
    default void onEditorSave(JEFBContext ctx, Path file, String content) {}

    interface BuildContext {
        Path getProjectRoot();
        Path getClassesDir();
        List<String> getCompilerArgs();
        List<Path> getClasspath();
        String getProjectName();
        String getProjectVersion();
        int getJavaRelease();
    }

    interface JEFBContext {

        void addToolbarButton(String label, String iconName, Runnable onClick);
        void addMenuItem(String menu, String label, Runnable onClick);
        void addConsoleCommand(String name, Consumer<List<String>> handler);
        void addEditorContextMenu(String label, Runnable onClick);

        void log(String msg);
        void logError(String msg);
        void logWarn(String msg);
        void clearConsole();
        void appendConsole(String line);

        Path getProjectRoot();
        String getProjectName();
        String getProjectVersion();
        Path getSourceRoot();
        Path getResourcesRoot();
        Path getLibRoot();
        Path getBuildRoot();

        Path getCurrentFilePath();
        String getCurrentFileName();
        String getCurrentFileContent();
        void setCurrentFileContent(String content);
        void insertAtCursor(String text);
        void replaceSelection(String text);
        String getSelection();
        int getCaretPosition();
        void setCaretPosition(int pos);
        int getLineNumber();
        void goToLine(int line);
        void openFileInEditor(Path path);
        void openTextInEditor(String title, String content);
        void closeCurrentTab();
        List<Path> getOpenFiles();

        List<Path> listProjectFiles();
        List<Path> listJavaFiles();
        String readFile(Path path);
        void writeFile(Path path, String content);
        boolean fileExists(Path path);
        void deleteFile(Path path);
        Path resolveProject(String relative);

        boolean runBuild();
        boolean runProject();
        boolean packageJar();
        void cleanBuild();
        void saveCurrentFile();
        void saveAllFiles();

        void setStatus(String text);
        void showMessage(String msg);
        void showError(String msg);
        String promptInput(String title, String defaultValue);
        boolean confirm(String title, String message);

        void setLineHighlight(int line, java.awt.Color color);
        void clearLineHighlights();

        javax.swing.JFrame getFrame();
        javax.swing.JTextArea getConsole();

        void addCompilerArg(String arg);
        void removeCompilerArg(String arg);
        List<String> getCompilerArgs();

        void addClasspathEntry(Path jarOrDir);
        void removeClasspathEntry(Path jarOrDir);
        List<Path> getClasspathEntries();

        void setSourceDirectory(Path dir);
        void setOutputDirectory(Path dir);

        void addAnnotationProcessor(String processorClass);
        void setProcessorPath(List<Path> paths);

        void setCustomCompiler(JavaCompiler compiler);

        void addJarEntry(String entryName, Path sourceFile);

        void registerPreCompileHook(Consumer<BuildContext> hook);
        void registerPostCompileHook(Consumer<BuildContext> hook);
        void registerDiagnosticHandler(Consumer<Diag> handler);
        void registerErrorHandler(Consumer<Throwable> handler);
    }
}