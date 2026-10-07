package ir.IrAutoX.JEFB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PluginDialog {

    static List<Plugin.Info> CACHE = new ArrayList<>();

    public static void show() {
        JDialog d = new JDialog(IDE.frame, "Plugins", false);
        d.setSize(780, 520);
        d.setLocationRelativeTo(IDE.frame);

        DefaultListModel<Plugin.Info> model = new DefaultListModel<>();
        JList<Plugin.Info> list = new JList<>(model);
        list.setCellRenderer(new PluginRenderer());
        list.setFixedCellHeight(48);

        JLabel status = new JLabel(" ");

        Runnable reload = () -> {
            model.clear();
            CACHE.clear();
            CACHE.addAll(Plugin.discover());
            for (Plugin.Info p : CACHE) model.addElement(p);
            status.setText(CACHE.size() + " plugin(s) installed");
        };

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton install = new JButton("Install JAR...");
        JButton uninstall = new JButton("Uninstall");
        JButton trust = new JButton("Trust Publisher");
        JButton reloadBtn = new JButton("Refresh");
        top.add(install);
        top.add(uninstall);
        top.add(trust);
        top.add(reloadBtn);

        install.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(IDE.frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    Plugin.install(fc.getSelectedFile().toPath());
                    reload.run();
                    IDE.appendConsole("Plugin installed: " + fc.getSelectedFile().getName());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(IDE.frame,
                            "Install failed: " + Log.msg(ex),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        uninstall.addActionListener(e -> {
            Plugin.Info info = list.getSelectedValue();
            if (info == null) return;
            try {
                Plugin.uninstall(info.jar);
                reload.run();
                IDE.appendConsole("Plugin uninstalled: " + info.name);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(IDE.frame,
                        "Uninstall failed: " + Log.msg(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        trust.addActionListener(e -> {
            Plugin.Info info = list.getSelectedValue();
            if (info == null) return;
            try {
                Plugin.trust(info.jar);
                reload.run();
                IDE.appendConsole("Trusted publisher added for " + info.name);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(IDE.frame,
                        "Trust failed: " + Log.msg(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        reloadBtn.addActionListener(e -> reload.run());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.WEST);

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.setVisible(true);
        reload.run();
    }

    static class PluginRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof Plugin.Info p) {
                String title = p.name + "  v" + p.version;
                String sub = p.trusted
                        ? "Trusted publisher: " + (p.publisher == null ? "unknown" : p.publisher)
                        : "Untrusted — signature missing or key not trusted";
                String color = p.trusted ? "#4CAF50" : "#FF9800";
                setText("<html><b>" + title + "</b><br><font color='" + color
                        + "' size='3'>" + sub + "</font></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

                com.formdev.flatlaf.extras.FlatSVGIcon icon = p.trusted
                        ? IdeIcons.get("verify", 24)
                        : IdeIcons.get("plugin", 24);
                if (icon != null) setIcon(icon);
            }
            return this;
        }
    }

    private PluginDialog() {}
}