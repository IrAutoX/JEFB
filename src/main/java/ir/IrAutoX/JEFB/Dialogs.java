package ir.IrAutoX.JEFB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class Dialogs {

    public static void about() {
        JOptionPane.showMessageDialog(IDE.frame,
                JBuild.NAME + " " + JBuild.VERSION + "\n\n" +
                        "Author   : " + JBuild.AUTHOR + "\n" +
                        "Developer: " + JBuild.DEVELOPER + "\n\n" +
                        "JEF Builder is a lightweight Java IDE and build tool\n" +
                        "with GitHub update checks, Maven Central integration,\n" +
                        "Trusted Publisher signing, package manager, plugin SDK,\n" +
                        "SVG/PNG preview, editable compiler API, and SHA-256 encrypted caches.",
                "About " + JBuild.NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void newProject() {
        JTextField nameF = new JTextField("MyApp");
        JTextField locF = new JTextField(System.getProperty("user.home"));
        JTextField mainF = new JTextField("Main");
        JTextField javaF = new JTextField("17");
        JCheckBox mixinsBox = new JCheckBox("Minecraft mod (enable Mixin)", false);
        JButton browseBtn = new JButton("...");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(IDE.frame) == JFileChooser.APPROVE_OPTION)
                locF.setText(fc.getSelectedFile().getAbsolutePath());
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;
        addRow(form, gbc, y++, "Project name:", nameF);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
        form.add(new JLabel("Location:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel locPanel = new JPanel(new BorderLayout(4, 0));
        locPanel.add(locF, BorderLayout.CENTER);
        locPanel.add(browseBtn, BorderLayout.EAST);
        form.add(locPanel, gbc);
        y++;

        addRow(form, gbc, y++, "Java version:", javaF);
        addRow(form, gbc, y++, "Main class:", mainF);

        gbc.gridx = 0; gbc.gridy = y; gbc.gridwidth = 2; gbc.weightx = 1;
        form.add(mixinsBox, gbc);

        if (JOptionPane.showConfirmDialog(IDE.frame, form,
                "New JEF Builder Project",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        String name = nameF.getText().trim();
        String loc = locF.getText().trim();
        String main = mainF.getText().trim();
        String jv = javaF.getText().trim();
        boolean mixins = mixinsBox.isSelected();
        if (name.isEmpty() || loc.isEmpty()) return;

        Path root = Paths.get(loc).resolve(name);
        try {
            Files.createDirectories(root.resolve("src/main/java"));
            Files.createDirectories(root.resolve("src/main/resources"));
            Files.createDirectories(root.resolve("lib"));
            Files.createDirectories(root.resolve("build"));
            Files.createDirectories(root.resolve("icons"));
            String body =
                    "name=" + name + "\n" +
                    "version=1.0.0\n" +
                    "java=" + jv + "\n" +
                    "main=" + main + "\n" +
                    "source=src/main/java\n" +
                    "resources=src/main/resources\n" +
                    "libraries=\n" +
                    "signature=\n" +
                    "mixins=" + mixins + "\n" +
                    "mixinConfig=mixins.json\n" +
                    "compiler.args=\n" +
                    "compiler.classpath=\n" +
                    "compiler.processor=\n" +
                    "compiler.custom=\n";
            IO.write(root.resolve(Project.CONFIG_FILE), body);

            if (!main.isEmpty()) {
                Path mf = root.resolve("src/main/java")
                        .resolve(main.replace('.', '/') + ".java");
                Files.createDirectories(mf.getParent());
                if (!Files.exists(mf)) {
                    String pkg = main.contains(".")
                            ? main.substring(0, main.lastIndexOf('.')) : "";
                    String cls = main.contains(".")
                            ? main.substring(main.lastIndexOf('.') + 1) : main;
                    IO.write(mf, ExplorerActions.swingTemplate(pkg, cls));
                }
            }
            if (mixins) {
                Path mix = root.resolve("src/main/resources/mixins.json");
                String mixBody = "{\n" +
                        "  \"required\": true,\n" +
                        "  \"minVersion\": \"0.8\",\n" +
                        "  \"package\": \"com.example.mixin\",\n" +
                        "  \"compatibilityLevel\": \"JAVA_17\",\n" +
                        "  \"refmap\": \"" + name.toLowerCase() + ".refmap.json\",\n" +
                        "  \"mixins\": [],\n" +
                        "  \"client\": [],\n" +
                        "  \"server\": []\n" +
                        "}\n";
                IO.write(mix, mixBody);
                Path modsToml = root.resolve("src/main/resources/META-INF/mods.toml");
                String modsBody =
                        "modLoader=\"javafml\"\n" +
                        "loaderVersion=\"[47,)\"\n" +
                        "license=\"MIT\"\n\n" +
                        "[[mods]]\n" +
                        "modId=\"" + name.toLowerCase() + "\"\n" +
                        "version=\"1.0.0\"\n" +
                        "displayName=\"" + name + "\"\n" +
                        "description=\"A Minecraft mod built with JEFB\"\n\n" +
                        "[[dependencies." + name.toLowerCase() + "]]\n" +
                        "modId=\"forge\"\n" +
                        "mandatory=true\n" +
                        "versionRange=\"[47,)\"\n" +
                        "ordering=\"NONE\"\n" +
                        "side=\"BOTH\"\n\n" +
                        "[[dependencies." + name.toLowerCase() + "]]\n" +
                        "modId=\"minecraft\"\n" +
                        "mandatory=true\n" +
                        "versionRange=\"[1.20.1,1.21)\"\n" +
                        "ordering=\"NONE\"\n" +
                        "side=\"BOTH\"\n";
                IO.write(modsToml, modsBody);
            }
            IDE.project = Project.load(root);
            IDE.projectRoot = root;
            IDE.rememberRecent(root);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(IDE.frame, "Create failed: " + Log.msg(e),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void openProject() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(IDE.frame) != JFileChooser.APPROVE_OPTION) return;
        Path root = fc.getSelectedFile().toPath().toAbsolutePath().normalize();
        try {
            IDE.project = Project.load(root);
            IDE.projectRoot = root;
            IDE.rememberRecent(root);
        } catch (BuildException e) {
            int create = JOptionPane.showConfirmDialog(IDE.frame,
                    "Not a JEF Builder project. Create jefb.conf here?",
                    "Not a project", JOptionPane.YES_NO_OPTION);
            if (create == JOptionPane.YES_OPTION) {
                try {
                    String name = root.getFileName() == null ? "app" : root.getFileName().toString();
                    String body =
                            "name=" + name + "\nversion=1.0.0\njava=17\nmain=Main\n" +
                            "source=src/main/java\nresources=src/main/resources\n" +
                            "libraries=\nsignature=\n" +
                            "mixins=false\nmixinConfig=mixins.json\n" +
                            "compiler.args=\ncompiler.classpath=\n" +
                            "compiler.processor=\ncompiler.custom=\n";
                    IO.write(root.resolve(Project.CONFIG_FILE), body);
                    Files.createDirectories(root.resolve("src/main/java"));
                    Files.createDirectories(root.resolve("src/main/resources"));
                    Files.createDirectories(root.resolve("lib"));
                    Files.createDirectories(root.resolve("build"));
                    Files.createDirectories(root.resolve("icons"));
                    IDE.project = Project.load(root);
                    IDE.projectRoot = root;
                    IDE.rememberRecent(root);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(IDE.frame, "Init failed: " + Log.msg(ex),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    public static void settings() {
        JCheckBox darkBox = new JCheckBox("Dark mode", IDE.darkMode);
        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        form.add(darkBox);
        if (JOptionPane.showConfirmDialog(IDE.frame, form, "Settings",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
            return;
        boolean newDark = darkBox.isSelected();
        if (newDark != IDE.darkMode) {
            IDE.darkMode = newDark;
            try {
                if (IDE.darkMode) com.formdev.flatlaf.FlatDarkLaf.setup();
                else com.formdev.flatlaf.FlatLightLaf.setup();
                SwingUtilities.updateComponentTreeUI(IDE.frame);
                IdeIcons.clearCache();
            } catch (Exception ignored) {}
        }
    }

    public static void compilerSettings() {
        if (IDE.project == null) {
            JOptionPane.showMessageDialog(IDE.frame, "No project loaded.",
                    "Compiler Settings", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Project p = IDE.project;
        JTextField argsF = new JTextField(p.compilerArgs(), 40);
        JTextField cpF = new JTextField(p.compilerClasspath(), 40);
        JTextField procF = new JTextField(p.compilerProcessor(), 40);
        JTextField customF = new JTextField(p.customCompiler(), 40);
        JCheckBox mixinsBox = new JCheckBox("Enable Mixin (Minecraft mod)", p.mixins());
        JTextField mixinCfgF = new JTextField(p.mixinConfig(), 40);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;
        addRow(form, gbc, y++, "Extra compiler args:", argsF);
        addRow(form, gbc, y++, "Extra classpath:", cpF);
        addRow(form, gbc, y++, "Annotation processor:", procF);
        addRow(form, gbc, y++, "Custom compiler class:", customF);
        addRow(form, gbc, y++, "Mixin config:", mixinCfgF);
        gbc.gridx = 0; gbc.gridy = y; gbc.gridwidth = 2; gbc.weightx = 1;
        form.add(mixinsBox, gbc);

        if (JOptionPane.showConfirmDialog(IDE.frame, form,
                "Compiler Settings",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            p.updateCompilerArgs(argsF.getText().trim());
            p.updateCompilerClasspath(cpF.getText().trim());
            p.updateCompilerProcessor(procF.getText().trim());
            p.updateCustomCompiler(customF.getText().trim());
            p.updateMixins(mixinsBox.isSelected());
            p.updateMixinConfig(mixinCfgF.getText().trim());
            IDE.project = Project.load(p.root);
            IDE.appendConsole("Compiler settings saved.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(IDE.frame, "Save failed: " + Log.msg(e),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void openJarOrClass() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileFilter() {
            @Override public boolean accept(File f) {
                String n = f.getName().toLowerCase();
                return f.isDirectory() || n.endsWith(".jar") || n.endsWith(".class");
            }
            @Override public String getDescription() { return "JAR or Class files"; }
        });
        if (fc.showOpenDialog(IDE.frame) != JFileChooser.APPROVE_OPTION) return;
        Path p = fc.getSelectedFile().toPath();
        new Thread(() -> {
            try {
                String code = DecompilerUtil.decompile(p);
                SwingUtilities.invokeLater(() ->
                        IDE.openDecompiled(p.getFileName() + ".java", code));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(IDE.frame,
                        "Decompile failed: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE));
            }
        }, "jefb-decomp").start();
    }

    public static void libraries() {
        if (IDE.project == null) {
            JOptionPane.showMessageDialog(IDE.frame, "No project loaded.",
                    "Libraries", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea libs = new JTextArea(12, 50);
        libs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        try {
            List<Path> jars = LibraryManager.listJars(IDE.project);
            StringBuilder sb = new StringBuilder();
            for (Path j : jars) sb.append(j.toAbsolutePath()).append("\n");
            libs.setText(sb.toString());
        } catch (IOException ignored) {}

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Project libraries (lib/ folder):"), BorderLayout.NORTH);
        panel.add(new JScrollPane(libs), BorderLayout.CENTER);

        JButton openFolder = new JButton("Open lib/ folder");
        openFolder.addActionListener(e -> {
            try { Desktop.getDesktop().open(IDE.project.root.resolve("lib").toFile()); }
            catch (Exception ignored) {}
        });
        panel.add(openFolder, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(IDE.frame, panel,
                "Libraries", JOptionPane.PLAIN_MESSAGE);
    }

    static void addRow(JPanel p, GridBagConstraints gbc, int y, String label, JComponent field) {
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
        p.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(field, gbc);
    }

    private Dialogs() {}
}