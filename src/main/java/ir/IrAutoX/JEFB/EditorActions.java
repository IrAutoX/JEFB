package ir.IrAutoX.JEFB;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditorActions {

    public static void saveCurrent() {
        EditorPane ep = IDE.currentEditor();
        if (ep == null) return;
        try {
            String content = ep.text.getText();
            IO.write(ep.file, content);
            ep.dirty = false;
            IDE.appendConsole("Saved: " + ep.file.getFileName());
            try { PluginRuntime.fireEditorSave(ep.file, content); }
            catch (Throwable ignored) {}
        } catch (IOException e) {
            JOptionPane.showMessageDialog(IDE.frame, "Save failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void saveAll() {
        for (int i = 0; i < IDE.editorTabs.getTabCount(); i++) {
            Component c = IDE.editorTabs.getComponentAt(i);
            if (c instanceof EditorPane ep && ep.dirty) {
                try {
                    String content = ep.text.getText();
                    IO.write(ep.file, content);
                    ep.dirty = false;
                    try { PluginRuntime.fireEditorSave(ep.file, content); }
                    catch (Throwable ignored) {}
                } catch (IOException ignored) {}
            }
        }
        IDE.appendConsole("Saved all.");
    }

    public static void closeCurrent() {
        int idx = IDE.editorTabs.getSelectedIndex();
        if (idx < 0) return;
        Component c = IDE.editorTabs.getComponentAt(idx);
        if (c instanceof EditorPane ep && ep.dirty) {
            int r = JOptionPane.showConfirmDialog(IDE.frame,
                    "Save changes to " + ep.file.getFileName() + "?",
                    "Unsaved", JOptionPane.YES_NO_CANCEL_OPTION);
            if (r == JOptionPane.CANCEL_OPTION) return;
            if (r == JOptionPane.YES_OPTION) {
                IDE.editorTabs.setSelectedIndex(idx);
                saveCurrent();
            }
        }
        IDE.editorTabs.remove(idx);
    }

    public static void undo() {
        EditorPane ep = IDE.currentEditor();
        if (ep != null && ep.undoManager.canUndo()) ep.undoManager.undo();
    }

    public static void redo() {
        EditorPane ep = IDE.currentEditor();
        if (ep != null && ep.undoManager.canRedo()) ep.undoManager.redo();
    }

    public static void openAt(String relFile, int line) {
        if (relFile == null) return;
        Path p = IDE.projectRoot.resolve(relFile);
        IDE.openEditor(p);
        EditorPane ep = IDE.currentEditor();
        if (ep == null) return;
        try {
            int off = ep.text.getLineStartOffset(Math.max(0, line - 1));
            ep.text.setCaretPosition(off);
            ep.text.requestFocusInWindow();
        } catch (Exception ignored) {}
    }

    public static void setCurrentAsMain() {
        EditorPane ep = IDE.currentEditor();
        if (ep == null || IDE.project == null) return;
        String fqcn = className(ep.file);
        if (fqcn == null) return;
        try {
            IDE.project.updateMain(fqcn);
            IDE.project = Project.load(IDE.project.root);
            IDE.appendConsole("Main class set: " + fqcn);
        } catch (Exception e) {
            IDE.appendConsole("Failed: " + Log.msg(e));
        }
    }

    public static String className(Path file) {
        try {
            String content = IO.read(file);
            Matcher m = Pattern.compile(
                    "\\b(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)")
                    .matcher(content);
            String cls = null;
            while (m.find()) { cls = m.group(1); break; }
            if (cls == null) return null;
            Matcher pm = Pattern.compile(
                    "^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;",
                    Pattern.MULTILINE).matcher(content);
            String pkg = pm.find() ? pm.group(1) : "";
            return pkg.isEmpty() ? cls : pkg + "." + cls;
        } catch (IOException e) { return null; }
    }

    private EditorActions() {}
}