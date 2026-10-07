package ir.IrAutoX.JEFB;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IDE {

    public static boolean darkMode = true;

    public static Project project;
    public static JFrame frame;
    public static JTree projectTree;
    public static DefaultTreeModel treeModel;
    public static DefaultMutableTreeNode treeRoot;
    public static JTabbedPane editorTabs;
    public static JTextArea console;
    public static DefaultTableModel problemsModel;
    public static JTable problemsTable;
    public static JLabel statusLabel;
    public static JLabel statusRight;
    public static Path projectRoot;
    public static JToolBar mainToolbar;

    static final ExecutorService BG = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "jefb-bg");
        t.setDaemon(true);
        return t;
    });

    static final String RECENT_FILE = JBuild.SETTINGS_DIR
            + File.separator + "recent.txt";

    public static void rememberRecent(Path root) {
        try {
            Files.createDirectories(Paths.get(JBuild.SETTINGS_DIR));
            Path rf = Paths.get(RECENT_FILE);
            List<String> list = Files.isRegularFile(rf)
                    ? Files.readAllLines(rf) : new ArrayList<>();
            list.remove(root.toString());
            list.add(0, root.toString());
            if (list.size() > 12) list = list.subList(0, 12);
            Files.write(rf, list, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static void launch(Project p) {
        project = p;
        projectRoot = p != null ? p.root : Paths.get(".").toAbsolutePath().normalize();
        SwingUtilities.invokeLater(IDE::buildUI);
    }

    public static void buildUI() {
        try {
            if (darkMode) FlatDarkLaf.setup();
            else FlatLightLaf.setup();
        } catch (Exception ignored) {}

        applyGlobalUI();

        frame = new JFrame(JBuild.SHORT + " " + JBuild.VERSION + "  -  " + JBuild.NAME);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1360, 860);
        frame.setLocationRelativeTo(null);
        frame.setMinimumSize(new Dimension(900, 600));
        applyAppIcon(frame);

        if (project == null) {
            frame.setContentPane(new WelcomePanel());
            frame.setJMenuBar(buildWelcomeMenuBar());
            frame.setVisible(true);
            SwingUtilities.invokeLater(IDE::applyPluginUi);
            return;
        }

        frame.setJMenuBar(buildMenuBar());
        JPanel root = new JPanel(new BorderLayout());
        mainToolbar = buildToolbar();
        root.add(mainToolbar, BorderLayout.NORTH);
        root.add(buildMainSplit(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setVisible(true);
        refreshTree();
        appendConsole("Loaded project: " + project.name);
        appendConsole("Root: " + projectRoot);

        SwingUtilities.invokeLater(IDE::applyPluginUi);

        BG.submit(IDE::checkUpdatesSilent);
    }

    static void applyGlobalUI() {
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabHeight", 32);
        UIManager.put("TabbedPane.tabInsets", new Insets(6, 14, 6, 14));
        UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(6, 14, 6, 14));
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("Table.rowHeight", 24);
        UIManager.put("Tree.rowHeight", 24);
        UIManager.put("Tree.paintLines", false);
        UIManager.put("Tree.expandedIcon", null);
        UIManager.put("Tree.collapsedIcon", null);
        UIManager.put("MenuBar.borderColor", new Color(0x3c3f41));
        UIManager.put("MenuItem.selectionType", "underline");
        UIManager.put("MenuItem.selectionBackground", new Color(0x2d5a8c));
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("OptionPane.messageForeground", UIManager.getColor("Label.foreground"));
        UIManager.put("OptionPane.background", UIManager.getColor("Panel.background"));
        UIManager.put("OptionPane.messageAreaBorder", BorderFactory.createEmptyBorder(12, 12, 12, 12));
        UIManager.put("OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(8, 12, 12, 12));
        UIManager.put("ToolTip.background", new Color(0x2b2b2b));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(new Color(0x4a4a4a)));
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("SplitPane.background", new Color(0x3c3f41));
    }

    public static void applyPluginUi() {
        if (frame == null) return;
        JMenuBar bar = frame.getJMenuBar();
        if (bar == null) return;

        Map<String, Map<String, Runnable>> menus = PluginRuntime.getMenuItems();
        for (Map.Entry<String, Map<String, Runnable>> menuEntry : menus.entrySet()) {
            String menuName = menuEntry.getKey();
            JMenu target = null;
            for (int i = 0; i < bar.getMenuCount(); i++) {
                JMenu m = bar.getMenu(i);
                if (m != null && menuName.equals(m.getText())) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                target = new JMenu(menuName);
                bar.add(target);
            }
            for (Map.Entry<String, Runnable> item : menuEntry.getValue().entrySet()) {
                JMenuItem mi = new JMenuItem(item.getKey());
                final Runnable action = item.getValue();
                mi.addActionListener(e -> {
                    try { action.run(); }
                    catch (Throwable t) { handlePluginError("Menu action", t); }
                });
                target.add(mi);
            }
        }

        if (mainToolbar != null) {
            Map<String, Runnable> buttons = PluginRuntime.getToolbarButtons();
            if (!buttons.isEmpty()) {
                mainToolbar.addSeparator(new Dimension(12, 0));
                for (Map.Entry<String, Runnable> e : buttons.entrySet()) {
                    JButton b = new JButton(e.getKey());
                    b.setToolTipText(e.getKey());
                    b.setFocusPainted(false);
                    final Runnable action = e.getValue();
                    b.addActionListener(ev -> {
                        try { action.run(); }
                        catch (Throwable t) { handlePluginError("Toolbar action", t); }
                    });
                    mainToolbar.add(b);
                }
            }
            mainToolbar.revalidate();
            mainToolbar.repaint();
        }

        bar.revalidate();
        bar.repaint();
    }

    static void handlePluginError(String ctx, Throwable t) {
        String msg = Log.msg(t);
        appendConsole("[plugin ERROR] " + ctx + ": " + msg);
        try { PluginRuntime.fireError(t); } catch (Throwable ignored) {}
        if (Log.debug) t.printStackTrace();
    }

    static JToolBar findToolbar(Container c) {
        if (c instanceof JToolBar tb) return tb;
        if (c instanceof Container) {
            for (Component child : c.getComponents()) {
                if (child instanceof Container) {
                    JToolBar t = findToolbar((Container) child);
                    if (t != null) return t;
                }
            }
        }
        return null;
    }

    static JMenuBar buildWelcomeMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(menuItem("New Project...", "ctrl N", e -> {
            Dialogs.newProject();
            if (project != null) { frame.dispose(); buildUI(); }
        }));
        file.add(menuItem("Open Project...", "ctrl O", e -> {
            Dialogs.openProject();
            if (project != null) { frame.dispose(); buildUI(); }
        }));
        file.addSeparator();
        file.add(menuItem("Exit", "ctrl Q", e -> System.exit(0)));
        bar.add(file);
        JMenu help = new JMenu("Help");
        help.add(menuItem("About", e -> Dialogs.about()));
        bar.add(help);
        return bar;
    }

    static JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("New Project...", "ctrl N", e -> Dialogs.newProject()));
        file.add(menuItem("Open Project...", "ctrl O", e -> Dialogs.openProject()));
        file.add(menuItem("Close Project", e -> {
            project = null;
            projectRoot = Paths.get(".").toAbsolutePath().normalize();
            frame.dispose();
            buildUI();
        }));
        file.addSeparator();
        file.add(menuItem("New File", "ctrl shift N", e -> ExplorerActions.newFileSmart()));
        file.add(menuItem("New Folder", e -> ExplorerActions.newFolder()));
        file.addSeparator();
        file.add(menuItem("Open JAR/Class...", e -> Dialogs.openJarOrClass()));
        file.addSeparator();
        file.add(menuItem("Save", "ctrl S", e -> EditorActions.saveCurrent()));
        file.add(menuItem("Save All", "ctrl shift S", e -> EditorActions.saveAll()));
        file.add(menuItem("Close Tab", "ctrl W", e -> EditorActions.closeCurrent()));
        file.addSeparator();
        file.add(menuItem("Exit", "ctrl Q", e -> System.exit(0)));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.add(menuItem("Undo", "ctrl Z", e -> EditorActions.undo()));
        edit.add(menuItem("Redo", "ctrl Y", e -> EditorActions.redo()));
        bar.add(edit);

        JMenu projectMenu = new JMenu("Project");
        projectMenu.add(menuItem("Refresh", "F5", e -> refreshTree()));
        projectMenu.add(menuItem("Build", "F9", e -> runBuild()));
        projectMenu.add(menuItem("Clean", e -> runClean()));
        projectMenu.addSeparator();
        projectMenu.add(menuItem("Set as Main Class", e -> EditorActions.setCurrentAsMain()));
        projectMenu.add(menuItem("Libraries...", e -> Dialogs.libraries()));
        projectMenu.add(menuItem("Compiler Settings...", e -> Dialogs.compilerSettings()));
        projectMenu.add(menuItem("Package Manager...", e -> PackageManagerDialog.show()));
        projectMenu.add(menuItem("Plugins...", e -> PluginDialog.show()));
        projectMenu.add(menuItem("Trusted Publishers...", e -> TrustedPublisherDialog.show()));
        projectMenu.addSeparator();
        projectMenu.add(menuItem("Sign Manager...", e -> SignManagerDialog.show()));
        projectMenu.add(menuItem("Sign Build", e -> runSign()));
        projectMenu.add(menuItem("Verify Build", e -> runVerify()));
        projectMenu.addSeparator();
        projectMenu.add(menuItem("Check for Updates...", e -> BG.submit(Updater::checkNow)));
        bar.add(projectMenu);

        JMenu build = new JMenu("Build");
        build.add(menuItem("Build", "F9", e -> runBuild()));
        build.add(menuItem("Run", "F10", e -> runRun()));
        build.add(menuItem("Package JAR", e -> runJar()));
        build.add(menuItem("Clean", e -> runClean()));
        bar.add(build);

        JMenu help = new JMenu("Help");
        help.add(menuItem("About", e -> Dialogs.about()));
        help.add(menuItem("GitHub", e -> {
            try { Desktop.getDesktop().browse(URI.create("https://github.com/IrAutoX/JEFB")); }
            catch (Exception ignored) {}
        }));
        bar.add(help);

        return bar;
    }

    static JMenuItem menuItem(String text, ActionListener a) { return menuItem(text, null, a); }

    static JMenuItem menuItem(String text, String accel, ActionListener a) {
        JMenuItem it = new JMenuItem(text);
        it.addActionListener(a);
        if (accel != null) it.setAccelerator(KeyStroke.getKeyStroke(accel));
        return it;
    }

    static JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        tb.add(toolButton("Build (F9)", "build", e -> runBuild()));
        tb.add(toolButton("Run (F10)", "run", e -> runRun()));
        tb.add(toolButton("Package JAR", "jar", e -> runJar()));
        tb.add(toolButton("Clean", "stop", e -> runClean()));
        tb.addSeparator(new Dimension(12, 0));
        tb.add(toolButton("Save", "save", e -> EditorActions.saveCurrent()));
        tb.addSeparator(new Dimension(12, 0));
        tb.add(toolButton("New File", "newFile", e -> ExplorerActions.newFileSmart()));
        tb.add(toolButton("New Folder", "newFolder", e -> ExplorerActions.newFolder()));
        tb.add(toolButton("Refresh", "refresh", e -> refreshTree()));
        tb.add(Box.createHorizontalGlue());
        tb.add(toolButton("Libraries", "jar", e -> Dialogs.libraries()));
        tb.add(toolButton("Packages", "package", e -> PackageManagerDialog.show()));
        tb.add(toolButton("Plugins", "plugin", e -> PluginDialog.show()));
        tb.add(toolButton("Trusted", "verify", e -> TrustedPublisherDialog.show()));
        tb.add(toolButton("Sign Manager", "check", e -> SignManagerDialog.show()));
        tb.add(toolButton("Settings", "settings", e -> Dialogs.settings()));
        return tb;
    }

    static JButton toolButton(String tip, String iconName, ActionListener a) {
        JButton b = new JButton();
        FlatSVGIcon icon = IdeIcons.get(iconName, 20);
        if (icon != null) b.setIcon(icon);
        else b.setText("?");
        b.setToolTipText(tip);
        b.addActionListener(a);
        b.setFocusPainted(false);
        b.setMargin(new Insets(4, 8, 4, 8));
        return b;
    }

    static JSplitPane buildMainSplit() {
        projectTree = new JTree();
        projectTree.setRowHeight(24);
        projectTree.setCellRenderer(new ExplorerRenderer());
        projectTree.setRootVisible(true);
        projectTree.setShowsRootHandles(true);
        projectTree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) ExplorerActions.openSelected();
            }
            @Override public void mousePressed(MouseEvent e)  { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
        });
        JScrollPane treeScroll = new JScrollPane(projectTree);
        treeScroll.setBorder(null);
        JPanel explorerPanel = new JPanel(new BorderLayout());
        explorerPanel.add(treeScroll, BorderLayout.CENTER);

        JTabbedPane leftTabs = new JTabbedPane();
        leftTabs.addTab("  Explorer  ", explorerPanel);

        editorTabs = new JTabbedPane();
        editorTabs.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { editorPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { editorPopup(e); }
        });

        problemsModel = new DefaultTableModel(
                new Object[]{"Severity", "File", "Line", "Message"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        problemsTable = new JTable(problemsModel);
        problemsTable.setRowHeight(24);
        problemsTable.setShowGrid(false);
        problemsTable.setIntercellSpacing(new Dimension(0, 0));
        problemsTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = problemsTable.getSelectedRow();
                if (row < 0) return;
                String file = (String) problemsModel.getValueAt(row, 1);
                int line;
                try { line = Integer.parseInt(problemsModel.getValueAt(row, 2).toString()); }
                catch (NumberFormatException ex) { line = 1; }
                EditorActions.openAt(file, line);
            }
        });

        console = new JTextArea();
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        console.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JTabbedPane bottomTabs = new JTabbedPane();
        bottomTabs.addTab("  Console  ", new JScrollPane(console));
        bottomTabs.addTab("  Problems  ", new JScrollPane(problemsTable));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(bottomTabs, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(editorTabs, BorderLayout.CENTER);

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                centerPanel, bottomPanel);
        vertical.setDividerLocation(560);
        vertical.setResizeWeight(0.75);
        vertical.setBorder(null);

        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftTabs, vertical);
        horizontal.setDividerLocation(260);
        horizontal.setResizeWeight(0.18);
        horizontal.setBorder(null);
        return horizontal;
    }

    static void editorPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        List<PluginRuntime.EditorContextAction> actions = PluginRuntime.editorContextActions();
        JPopupMenu m = new JPopupMenu();

        m.add(menuItem("Undo", ev -> EditorActions.undo()));
        m.add(menuItem("Redo", ev -> EditorActions.redo()));
        m.addSeparator();
        m.add(menuItem("Save", ev -> EditorActions.saveCurrent()));
        m.add(menuItem("Close Tab", ev -> EditorActions.closeCurrent()));

        if (!actions.isEmpty()) {
            m.addSeparator();
            for (PluginRuntime.EditorContextAction a : actions) {
                final Runnable action = a.action;
                m.add(menuItem(a.label, ev -> {
                    try { action.run(); }
                    catch (Throwable t) { handlePluginError("Editor context action", t); }
                }));
            }
        }

        m.show(editorTabs, e.getX(), e.getY());
    }

    static JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel = new JLabel("Ready");
        statusRight = new JLabel(JBuild.AUTHOR + "  •  " + JBuild.DEVELOPER);
        statusRight.setForeground(new Color(0x888888));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(statusRight, BorderLayout.EAST);
        return bar;
    }

    static void setStatus(String s) { if (statusLabel != null) statusLabel.setText(s); }

    public static void refreshTree() {
        if (projectTree == null) return;
        if (projectRoot == null) return;
        if (!Files.isDirectory(projectRoot)) {
            appendConsole("Project root not a directory: " + projectRoot);
            return;
        }
        DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode(
                new FileNode(projectRoot));
        buildTree(projectRoot, newRoot, 0);
        treeRoot = newRoot;
        treeModel = new DefaultTreeModel(treeRoot);
        projectTree.setModel(treeModel);
        for (int i = 0; i < projectTree.getRowCount(); i++) {
            projectTree.expandRow(i);
        }
    }

    static void buildTree(Path dir, DefaultMutableTreeNode node, int depth) {
        if (depth > 8) return;
        List<Path> items = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (n.equals(".git") || n.equals(".idea") || n.equals("target")
                        || n.equals(".gradle") || n.equals("node_modules")) continue;
                items.add(p);
            }
        } catch (IOException e) {
            return;
        }
        items.sort(Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString().toLowerCase()));
        for (Path p : items) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(new FileNode(p));
            node.add(child);
            if (Files.isDirectory(p)) buildTree(p, child, depth + 1);
        }
    }

    public static final class FileNode {
        public final Path path;
        public FileNode(Path p) { path = p; }
        @Override public String toString() {
            if (path == null) return "";
            Path n = path.getFileName();
            return n == null ? path.toString() : n.toString();
        }
    }

    static class ExplorerRenderer extends DefaultTreeCellRenderer {
        @Override public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, focus);
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            if (value instanceof DefaultMutableTreeNode n
                    && n.getUserObject() instanceof FileNode fn) {
                ImageIcon icon = IdeIcons.getSystemIcon(fn.path);
                if (icon != null) setIcon(icon);
                else {
                    String name = fn.toString();
                    FlatSVGIcon svg;
                    if (Files.isDirectory(fn.path)) svg = IdeIcons.get("folder", 16);
                    else if (name.endsWith(".java")) svg = IdeIcons.get("file", 16);
                    else if (name.endsWith(".jar")) svg = IdeIcons.get("jar", 16);
                    else svg = IdeIcons.get("file", 16);
                    if (svg != null) setIcon(svg);
                }
            }
            return this;
        }
    }

    static void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        TreePath path = projectTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) projectTree.setSelectionPath(path);
        JPopupMenu m = new JPopupMenu();
        m.add(menuItem("Open", ev -> ExplorerActions.openSelected()));
        m.add(menuItem("Preview", ev -> ExplorerActions.previewSelected()));
        m.add(menuItem("Decompile", ev -> ExplorerActions.decompileSelected()));
        m.addSeparator();
        m.add(menuItem("Install as Plugin", ev -> ExplorerActions.installAsPlugin()));
        m.add(menuItem("Trust Publisher", ev -> ExplorerActions.trustSelected()));
        m.addSeparator();
        m.add(menuItem("New File...", ev -> ExplorerActions.newFileSmart()));
        m.add(menuItem("New Folder...", ev -> ExplorerActions.newFolder()));
        m.addSeparator();
        m.add(menuItem("Rename", ev -> ExplorerActions.rename()));
        m.add(menuItem("Delete", ev -> ExplorerActions.delete()));
        m.addSeparator();
        m.add(menuItem("Refresh", ev -> refreshTree()));
        m.show(projectTree, e.getX(), e.getY());
    }

    public static void appendConsole(String line) {
        if (console == null) return;
        SwingUtilities.invokeLater(() -> {
            console.append(line + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    public static void runBuild() {
        if (project == null) { appendConsole("No project loaded."); return; }
        appendConsole("> jefb build");
        setStatus("Building...");
        BG.submit(() -> {
            try { PluginRuntime.fireBuild(); } catch (Throwable t) { handlePluginError("onBuild", t); }
            PrintStream oldErr = System.err, oldOut = System.out;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            PrintStream tee = new PrintStream(new OutputStream() {
                @Override public void write(int b) { buf.write(b); oldErr.write(b); }
                @Override public void write(byte[] b, int off, int len) {
                    buf.write(b, off, len); oldErr.write(b, off, len);
                }
            }, true);
            System.setErr(tee);
            System.setOut(tee);
            Compiler.Result r;
            try { r = new Compiler(project).compile(); }
            catch (BuildException ex) {
                appendConsole("BUILD ERROR: " + ex.getMessage());
                if (Log.debug) ex.printStackTrace();
                r = null;
            } catch (Throwable t) {
                appendConsole("BUILD CRASH: " + Log.msg(t));
                t.printStackTrace();
                r = null;
            }
            finally { System.setErr(oldErr); System.setOut(oldOut); tee.flush(); }
            final Compiler.Result rr = r;
            final String cap = buf.toString();
            SwingUtilities.invokeLater(() -> {
                for (String line : cap.split("\\R")) if (!line.isBlank()) appendConsole(line);
                if (rr == null) { setStatus("Build error"); return; }
                problemsModel.setRowCount(0);
                for (Diag d : rr.diags)
                    problemsModel.addRow(new Object[]{d.sev.name(), d.file, d.line, d.msg});
                if (rr.ok) {
                    appendConsole("BUILD SUCCESS  compiled=" + rr.compiled + " cached=" + rr.skipped);
                    setStatus("Build OK");
                } else {
                    appendConsole("BUILD FAILED");
                    setStatus("Build failed");
                }
            });
        });
    }

    static void runRun() {
        if (project == null || project.mainClass.isBlank()) {
            appendConsole("No project / main class."); return;
        }
        appendConsole("> jefb run " + project.mainClass);
        BG.submit(() -> {
            try { PluginRuntime.fireRun(); } catch (Throwable t) { handlePluginError("onRun", t); }
            try {
                Compiler.Result r = new Compiler(project).compile();
                if (!r.ok) { SwingUtilities.invokeLater(() -> appendConsole("BUILD FAILED")); return; }
                List<String> cmd = new ArrayList<>();
                cmd.add(Commands.javaExe());
                cmd.add("-cp");
                String cp = project.classesPath().toString();
                try {
                    cp = Commands.joinCp(cp,
                            LibraryManager.buildClasspath(project),
                            LibraryManager.bundledClasspath());
                } catch (IOException ignored) {}
                cmd.add(cp);
                cmd.add(project.mainClass);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String l = line;
                        SwingUtilities.invokeLater(() -> appendConsole(l));
                    }
                }
                int code = proc.waitFor();
                SwingUtilities.invokeLater(() -> appendConsole("--- exit " + code + " ---"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendConsole("ERROR: " + Log.msg(ex)));
            }
        });
    }

    static void runJar() {
        if (project == null) { appendConsole("No project loaded."); return; }
        appendConsole("> jefb jar");
        setStatus("Packaging JAR...");
        BG.submit(() -> {
            try { PluginRuntime.fireJar(); } catch (Throwable t) { handlePluginError("onJar", t); }
            PrintStream oldErr = System.err, oldOut = System.out;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            PrintStream tee = new PrintStream(new OutputStream() {
                @Override public void write(int b) { buf.write(b); oldErr.write(b); }
                @Override public void write(byte[] b, int off, int len) {
                    buf.write(b, off, len); oldErr.write(b, off, len);
                }
            }, true);
            System.setErr(tee);
            System.setOut(tee);
            int code;
            try { code = Commands.jar(Collections.emptyList()); }
            finally { System.setErr(oldErr); System.setOut(oldOut); tee.flush(); }
            final String cap = buf.toString();
            final int cc = code;
            SwingUtilities.invokeLater(() -> {
                for (String line : cap.split("\\R")) if (!line.isBlank()) appendConsole(line);
                if (cc == 0) { appendConsole("JAR created."); setStatus("JAR created"); }
                else { appendConsole("Jar failed (exit " + cc + ")"); setStatus("Jar failed"); }
            });
        });
    }

    static void runSign() {
        if (project == null) return;
        BG.submit(() -> {
            int code = Commands.sign(Collections.emptyList());
            SwingUtilities.invokeLater(() ->
                    appendConsole(code == 0 ? "Signed." : "Sign failed."));
        });
    }

    static void runVerify() {
        if (project == null) return;
        BG.submit(() -> {
            int code = Commands.verify(Collections.emptyList());
            SwingUtilities.invokeLater(() -> appendConsole(code == 0
                    ? "Signature OK." : "Signature mismatch."));
        });
    }

    static void runClean() {
        if (project == null) return;
        BG.submit(() -> {
            int code = Commands.clean(Collections.emptyList());
            SwingUtilities.invokeLater(() -> appendConsole(code == 0 ? "Cleaned." : "Clean failed."));
        });
    }

    public static void openEditor(Path file) {
        if (file == null) return;
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component c = editorTabs.getComponentAt(i);
            if (c instanceof EditorPane ep && ep.file.equals(file)) {
                editorTabs.setSelectedIndex(i);
                return;
            }
        }
        try {
            EditorPane ep = new EditorPane(file);
            editorTabs.addTab(file.getFileName().toString(), ep);
            editorTabs.setSelectedComponent(ep);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                    "Cannot open: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void openPreview(Path file) {
        if (file == null) return;
        String title = "preview: " + file.getFileName();
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            if (title.equals(editorTabs.getTitleAt(i))) {
                editorTabs.setSelectedIndex(i); return;
            }
        }
        editorTabs.addTab(title, FilePreview.imagePreview(file));
        editorTabs.setSelectedIndex(editorTabs.getTabCount() - 1);
    }

    public static void openDecompiled(String title, String code) {
        EditorPane ep = EditorPane.fromString(title, code);
        editorTabs.addTab(title, ep);
        editorTabs.setSelectedComponent(ep);
    }

    public static EditorPane currentEditor() {
        Component c = editorTabs.getSelectedComponent();
        return c instanceof EditorPane ep ? ep : null;
    }

    static void applyAppIcon(JFrame f) {
        try {
            java.net.URL logoUrl = JBuild.class.getResource("/icons/Jau.png");
            if (logoUrl == null) return;
            Image src = new ImageIcon(logoUrl).getImage();
            int sw = src.getWidth(null), sh = src.getHeight(null);
            if (sw <= 0 || sh <= 0) return;
            int[] sizes = {16, 24, 32, 48, 64, 128, 256, 512};
            List<Image> images = new ArrayList<>();
            for (int s : sizes) {
                images.add(src.getScaledInstance(s, s, Image.SCALE_SMOOTH));
            }
            f.setIconImages(images);
        } catch (Exception ignored) {}
    }

    static void checkUpdatesSilent() {
        try {
            Updater.Release r = Updater.fetchLatest();
            if (r == null) return;
            String tag = r.tag.replaceAll("[^0-9.]", "");
            if (tag.isEmpty() || tag.equals(JBuild.VERSION)) return;
            SwingUtilities.invokeLater(() -> {
                int c = JOptionPane.showConfirmDialog(frame,
                        "A new version is available!\n\n" +
                                "Current : " + JBuild.VERSION + "\n" +
                                "Latest  : " + r.tag + "\n\n" +
                                "What's new:\n" + r.body + "\n\n" +
                                "Open release page?",
                        "Update Available", JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) {
                    try {
                        Desktop.getDesktop().browse(URI.create(
                                "https://github.com/IrAutoX/JEFB/releases"));
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private IDE() {}
}