package ir.IrAutoX.JEFB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExplorerActions {

    public static Path selectedPath() {
        TreePath tp = IDE.projectTree.getSelectionPath();
        if (tp == null) return IDE.projectRoot;
        Object o = ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject();
        if (o instanceof IDE.FileNode fn) return fn.path;
        return IDE.projectRoot;
    }

    public static Path selectedDir() {
        Path p = selectedPath();
        return Files.isDirectory(p) ? p : p.getParent();
    }

    public static void openSelected() {
        Path p = selectedPath();
        if (!Files.isRegularFile(p)) return;
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".class") || name.endsWith(".jar")) decompileSelected();
        else if (FilePreview.isImage(p) || FilePreview.isSvg(p)) IDE.openPreview(p);
        else IDE.openEditor(p);
    }

    public static void previewSelected() {
        Path p = selectedPath();
        if (Files.isRegularFile(p)) IDE.openPreview(p);
    }

    public static void decompileSelected() {
        Path p = selectedPath();
        if (p == null || !Files.isRegularFile(p)) return;
        String name = p.getFileName().toString();
        if (name.endsWith(".class") || name.endsWith(".jar")) {
            new Thread(() -> {
                try {
                    String code = DecompilerUtil.decompile(p);
                    SwingUtilities.invokeLater(() -> IDE.openDecompiled(name + ".java", code));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(IDE.frame,
                            "Decompile failed: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
            }, "jefb-decomp").start();
        }
    }

    public static void installAsPlugin() {
        Path p = selectedPath();
        if (p == null || !p.toString().toLowerCase().endsWith(".jar")) return;
        try {
            Plugin.install(p);
            IDE.appendConsole("Installed plugin: " + p.getFileName());
            JOptionPane.showMessageDialog(IDE.frame,
                    "Plugin installed to " + JBuild.PLUGINS_DIR,
                    "Plugins", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(IDE.frame, "Install failed: " + Log.msg(e),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void trustSelected() {
        Path p = selectedPath();
        if (p == null || !Files.isRegularFile(p)) return;
        try {
            Plugin.trust(p);
            IDE.appendConsole("Trusted publisher added for " + p.getFileName());
            JOptionPane.showMessageDialog(IDE.frame,
                    "Trusted publisher registered.",
                    "Trust", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(IDE.frame, "Trust failed: " + Log.msg(e),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void newFileSmart() {
        Path dir = selectedDir();
        JTextField nameF = new JTextField("Main");
        JCheckBox swingBox = new JCheckBox("Generate Swing UI template", true);
        JTextField pkgF = new JTextField("");
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        form.add(new JLabel("Class name:")); form.add(nameF);
        form.add(new JLabel("Package (optional):")); form.add(pkgF);
        form.add(new JLabel("")); form.add(swingBox);
        int r = JOptionPane.showConfirmDialog(IDE.frame, form,
                "New Java Class", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        String cls = nameF.getText().trim();
        String pkg = pkgF.getText().trim();
        if (cls.isEmpty()) return;
        if (!cls.endsWith(".java")) cls = cls + ".java";
        String raw = cls.substring(0, cls.length() - 5).replaceAll("[^A-Za-z0-9_]", "");
        if (raw.isEmpty() || !Character.isJavaIdentifierStart(raw.charAt(0))) return;
        raw = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        Path targetDir = dir;
        if (!pkg.isEmpty()) {
            Path srcRoot = IDE.project != null ? IDE.project.sourcePath() : dir;
            targetDir = srcRoot.resolve(pkg.replace('.', File.separatorChar));
        }
        Path target = targetDir.resolve(raw + ".java");
        if (Files.exists(target)) return;
        try {
            Files.createDirectories(target.getParent());
            String body = swingBox.isSelected()
                    ? swingTemplate(pkg, raw) : plainTemplate(pkg, raw);
            IO.write(target, body);
            IDE.refreshTree();
            IDE.openEditor(target);
            if (IDE.project != null && IDE.project.mainClass.isBlank()) {
                String fqcn = pkg.isEmpty() ? raw : pkg + "." + raw;
                try {
                    IDE.project.updateMain(fqcn);
                    IDE.project = Project.load(IDE.project.root);
                    IDE.appendConsole("Main class auto-set: " + fqcn);
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(IDE.frame, "Create failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static String plainTemplate(String pkg, String cls) {
        StringBuilder sb = new StringBuilder();
        if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");
        sb.append("public class ").append(cls).append(" {\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        System.out.println(\"Hello from ").append(cls).append("!\");\n");
        sb.append("    }\n}\n");
        return sb.toString();
    }

    public static String swingTemplate(String pkg, String cls) {
        StringBuilder sb = new StringBuilder();
        if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import javax.swing.*;\n");
        sb.append("import java.awt.*;\n\n");
        sb.append("public class ").append(cls).append(" {\n\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        SwingUtilities.invokeLater(").append(cls).append("::createUI);\n");
        sb.append("    }\n\n");
        sb.append("    static void createUI() {\n");
        sb.append("        JFrame frame = new JFrame(\"").append(cls).append("\");\n");
        sb.append("        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);\n");
        sb.append("        frame.setSize(800, 600);\n");
        sb.append("        frame.setLocationRelativeTo(null);\n\n");
        sb.append("        JLabel label = new JLabel(\"Hello from ").append(cls)
                .append("!\", SwingConstants.CENTER);\n");
        sb.append("        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));\n\n");
        sb.append("        JButton button = new JButton(\"Click Me\");\n");
        sb.append("        button.addActionListener(e ->\n");
        sb.append("                JOptionPane.showMessageDialog(frame, \"It works!\"));\n\n");
        sb.append("        JPanel bottom = new JPanel();\n");
        sb.append("        bottom.add(button);\n\n");
        sb.append("        frame.add(label, BorderLayout.CENTER);\n");
        sb.append("        frame.add(bottom, BorderLayout.SOUTH);\n");
        sb.append("        frame.setVisible(true);\n");
        sb.append("    }\n}\n");
        return sb.toString();
    }

    public static void newFolder() {
        Path dir = selectedDir();
        String name = JOptionPane.showInputDialog(IDE.frame, "Folder name:");
        if (name == null || name.isBlank()) return;
        try { Files.createDirectories(dir.resolve(name.trim())); IDE.refreshTree(); }
        catch (IOException ignored) {}
    }

    public static void rename() {
        Path p = selectedPath();
        if (p == null || p.equals(IDE.projectRoot)) return;
        String name = JOptionPane.showInputDialog(IDE.frame,
                "New name:", p.getFileName().toString());
        if (name == null || name.isBlank()) return;
        try {
            Files.move(p, p.resolveSibling(name.trim()));
            IDE.refreshTree();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(IDE.frame, "Rename failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void delete() {
        Path p = selectedPath();
        if (p == null || p.equals(IDE.projectRoot)) return;
        if (JOptionPane.showConfirmDialog(IDE.frame,
                "Delete " + p.getFileName() + "?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            if (Files.isDirectory(p)) IO.deleteRecursively(p);
            else Files.deleteIfExists(p);
            IDE.refreshTree();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(IDE.frame, "Delete failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private ExplorerActions() {}
}