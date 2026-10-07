package ir.IrAutoX.JEFB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class WelcomePanel extends JPanel {

    static final String RECENT_FILE = JBuild.SETTINGS_DIR
            + java.io.File.separator + "recent.txt";

    public WelcomePanel() {
        super(new BorderLayout(24, 24));
        setBorder(new EmptyBorder(40, 50, 40, 50));

        JPanel header = buildHeader();
        JPanel center = buildCenter();

        add(header, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(16, 8));

        JLabel logoLbl = new JLabel();
        try {
            java.net.URL logoUrl = JBuild.class.getResource("/icons/Jau.png");
            if (logoUrl != null) {
                Image img = new ImageIcon(logoUrl).getImage()
                        .getScaledInstance(72, 72, Image.SCALE_SMOOTH);
                logoLbl.setIcon(new ImageIcon(img));
            }
        } catch (Exception ignored) {}

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        JLabel title = new JLabel(JBuild.NAME + "  " + JBuild.VERSION);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("Lightweight Java IDE & Build Tool");
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 14f));
        sub.setForeground(new Color(0xaaaaaa));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel authors = new JLabel("by " + JBuild.AUTHOR + "  -  Developer: " + JBuild.DEVELOPER);
        authors.setFont(authors.getFont().deriveFont(Font.PLAIN, 12f));
        authors.setForeground(new Color(0x888888));
        authors.setAlignmentX(Component.LEFT_ALIGNMENT);

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(sub);
        titlePanel.add(Box.createVerticalStrut(6));
        titlePanel.add(authors);

        p.add(logoLbl, BorderLayout.WEST);
        p.add(titlePanel, BorderLayout.CENTER);
        return p;
    }

    JPanel buildCenter() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;

        JPanel left = buildActions();
        JPanel right = buildRecent();

        gbc.gridx = 0; gbc.weightx = 1;
        p.add(left, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(right, gbc);

        return p;
    }

    JPanel buildActions() {
        JPanel wrap = new JPanel(new BorderLayout(8, 8));
        wrap.setOpaque(false);

        JLabel heading = new JLabel("Start");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        wrap.add(heading, BorderLayout.NORTH);

        JPanel list = new JPanel(new GridLayout(0, 1, 6, 6));
        list.setOpaque(false);

        list.add(actionRow("welcome", "New Project", "Create a fresh JEF Builder project",
                e -> { Dialogs.newProject(); reloadAfterProjectChange(); }));
        list.add(actionRow("folder", "Open Project", "Open an existing project folder",
                e -> { Dialogs.openProject(); reloadAfterProjectChange(); }));
        list.add(actionRow("package", "Package Manager", "Browse and install libraries",
                e -> PackageManagerDialog.show()));
        list.add(actionRow("plugin", "Plugins", "Manage trusted plugins",
                e -> PluginDialog.show()));
        list.add(actionRow("verify", "Trusted Publishers", "Manage trusted signing keys",
                e -> TrustedPublisherDialog.show()));
        list.add(actionRow("save", "Sign Manager", "Configure signing metadata",
                e -> SignManagerDialog.showStandalone()));
        list.add(actionRow("refresh", "Check for Updates", "Look for the latest JEFB release",
                e -> new Thread(Updater::checkNow, "jefb-update").start()));
        list.add(actionRow("settings", "About", "Show information about JEFB",
                e -> Dialogs.about()));
        list.add(actionRow("close", "Exit", "Close JEF Builder",
                e -> System.exit(0)));

        wrap.add(list, BorderLayout.CENTER);
        return wrap;
    }

    JPanel actionRow(String iconName, String title, String subtitle, java.awt.event.ActionListener onClick) {
        JPanel row = new JPanel(new BorderLayout(12, 4));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel();
        com.formdev.flatlaf.extras.FlatSVGIcon svg = IdeIcons.get(iconName, 24);
        if (svg != null) icon.setIcon(svg);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 13f));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel s = new JLabel(subtitle);
        s.setFont(s.getFont().deriveFont(Font.PLAIN, 11f));
        s.setForeground(new Color(0x999999));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(t);
        textPanel.add(s);

        row.add(icon, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (onClick != null) onClick.actionPerformed(null);
            }
            @Override public void mouseEntered(MouseEvent e) {
                row.setOpaque(true);
                row.setBackground(new Color(0x3c3f41));
                row.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                row.setOpaque(false);
                row.repaint();
            }
        });
        return row;
    }

    JPanel buildRecent() {
        JPanel wrap = new JPanel(new BorderLayout(8, 8));
        wrap.setOpaque(false);

        JLabel heading = new JLabel("Recent Projects");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        wrap.add(heading, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String r : recentProjects()) model.addElement(r);

        if (model.isEmpty()) {
            JLabel empty = new JLabel("No recent projects yet");
            empty.setForeground(new Color(0x888888));
            wrap.add(empty, BorderLayout.CENTER);
            return wrap;
        }

        JList<String> list = new JList<>(model);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        list.setCellRenderer(new RecentRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String s = list.getSelectedValue();
                    if (s == null) return;
                    openRecent(s);
                }
            }
        });
        wrap.add(new JScrollPane(list), BorderLayout.CENTER);
        return wrap;
    }

    static void openRecent(String path) {
        try {
            Path r = Paths.get(path);
            IDE.project = Project.load(r);
            IDE.projectRoot = r;
            IDE.rememberRecent(r);
            if (IDE.frame != null) {
                IDE.frame.dispose();
                IDE.buildUI();
            }
        } catch (BuildException e) {
            JOptionPane.showMessageDialog(null,
                    "Cannot open: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static void reloadAfterProjectChange() {
        if (IDE.project != null && IDE.frame != null) {
            IDE.frame.dispose();
            IDE.buildUI();
        } else {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame f && f != IDE.frame) {
                    f.dispose();
                }
            }
            if (IDE.frame != null) {
                IDE.frame.dispose();
                IDE.buildUI();
            }
        }
    }

    static List<String> recentProjects() {
        try {
            Path rf = Paths.get(RECENT_FILE);
            if (!Files.isRegularFile(rf)) return java.util.Collections.emptyList();
            return Files.readAllLines(rf, StandardCharsets.UTF_8);
        } catch (IOException e) { return java.util.Collections.emptyList(); }
    }

    static class RecentRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof String path) {
                java.io.File f = new java.io.File(path);
                String name = f.getName();
                String parent = f.getParent();
                setText("<html><b>" + name + "</b><br><font color='#888888' size='3'>"
                        + parent + "</font></html>");
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

                com.formdev.flatlaf.extras.FlatSVGIcon icon = IdeIcons.get("folder", 20);
                if (icon != null) setIcon(icon);
            }
            return this;
        }
    }

    private static final long serialVersionUID = 1L;
}