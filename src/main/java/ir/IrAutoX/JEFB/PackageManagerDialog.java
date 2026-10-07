package ir.IrAutoX.JEFB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PackageManagerDialog {

    public static void show() {
        JDialog d = new JDialog(IDE.frame, "Package Manager", false);
        d.setSize(820, 560);
        d.setLocationRelativeTo(IDE.frame);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("  Libraries  ", buildLibsPanel(d));
        tabs.addTab("  Plugins  ", buildPluginsPanel(d));
        tabs.addTab("  Maven  ", buildMavenPanel(d));

        d.setContentPane(tabs);
        d.setVisible(true);
    }

    static JPanel buildLibsPanel(JDialog parent) {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        DefaultListModel<PackageRepository.Item> model = new DefaultListModel<>();
        JList<PackageRepository.Item> list = new JList<>(model);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        list.setCellRenderer(new PackageRenderer());

        JTextField searchF = new JTextField();
        JButton refreshBtn = new JButton("Refresh");
        JButton installBtn = new JButton("Install");
        JLabel statusL = new JLabel("Ready");

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(searchF, BorderLayout.CENTER);
        top.add(refreshBtn, BorderLayout.EAST);

        Runnable reload = () -> {
            statusL.setText("Fetching...");
            new Thread(() -> {
                List<PackageRepository.Item> items = PackageRepository.listLibs();
                SwingUtilities.invokeLater(() -> {
                    model.clear();
                    for (PackageRepository.Item it : items) model.addElement(it);
                    statusL.setText(items.size() + " libraries available");
                });
            }, "jefb-pm-libs").start();
        };

        searchF.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void update() {
                String q = searchF.getText().trim().toLowerCase();
                new Thread(() -> {
                    List<PackageRepository.Item> all = PackageRepository.listLibs();
                    SwingUtilities.invokeLater(() -> {
                        model.clear();
                        for (PackageRepository.Item it : all) {
                            if (q.isEmpty() || it.name.toLowerCase().contains(q))
                                model.addElement(it);
                        }
                        statusL.setText(model.size() + " result(s)");
                    });
                }, "jefb-pm-search").start();
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        refreshBtn.addActionListener(e -> reload.run());

        installBtn.addActionListener(e -> {
            PackageRepository.Item it = list.getSelectedValue();
            if (it == null || IDE.project == null) return;
            new Thread(() -> {
                try {
                    PackageRepository.installLib(it, IDE.project);
                    SwingUtilities.invokeLater(() -> {
                        statusL.setText("Installed: " + it.name);
                        IDE.appendConsole("Library installed: " + it.name);
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() ->
                            statusL.setText("Failed: " + Log.msg(ex)));
                }
            }, "jefb-pm-install").start();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(installBtn);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusL, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        reload.run();
        return root;
    }

    static JPanel buildPluginsPanel(JDialog parent) {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        DefaultListModel<PackageRepository.Item> model = new DefaultListModel<>();
        JList<PackageRepository.Item> list = new JList<>(model);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        list.setCellRenderer(new PackageRenderer());

        JButton refreshBtn = new JButton("Refresh");
        JButton installBtn = new JButton("Install & Load");
        JLabel statusL = new JLabel("Ready");

        Runnable reload = () -> {
            statusL.setText("Fetching...");
            new Thread(() -> {
                List<PackageRepository.Item> items = PackageRepository.listPlugins();
                SwingUtilities.invokeLater(() -> {
                    model.clear();
                    for (PackageRepository.Item it : items) model.addElement(it);
                    statusL.setText(items.size() + " plugins available");
                });
            }, "jefb-pm-plugins").start();
        };

        refreshBtn.addActionListener(e -> reload.run());
        installBtn.addActionListener(e -> {
            PackageRepository.Item it = list.getSelectedValue();
            if (it == null) return;
            new Thread(() -> {
                try {
                    PackageRepository.installPlugin(it);
                    SwingUtilities.invokeLater(() -> {
                        statusL.setText("Installed: " + it.name);
                        IDE.appendConsole("Plugin installed: " + it.name);
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() ->
                            statusL.setText("Failed: " + Log.msg(ex)));
                }
            }, "jefb-pm-plugin-install").start();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refreshBtn);
        top.add(installBtn);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        root.add(statusL, BorderLayout.SOUTH);

        reload.run();
        return root;
    }

    static JPanel buildMavenPanel(JDialog parent) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 2;

        JTextField groupF = new JTextField("org.json");
        JTextField artifactF = new JTextField("json");
        JTextField versionF = new JTextField("20240303");
        JLabel statusL = new JLabel("Enter Maven coordinates and click Download");

        gbc.gridx = 0; gbc.gridy = 0;
        root.add(new JLabel("Group ID:"), gbc);
        gbc.gridy = 1;
        root.add(groupF, gbc);
        gbc.gridy = 2;
        root.add(new JLabel("Artifact ID:"), gbc);
        gbc.gridy = 3;
        root.add(artifactF, gbc);
        gbc.gridy = 4;
        root.add(new JLabel("Version:"), gbc);
        gbc.gridy = 5;
        root.add(versionF, gbc);

        JButton downloadBtn = new JButton("Download from Maven Central");
        downloadBtn.addActionListener(e -> {
            if (IDE.project == null) return;
            new Thread(() -> {
                Path jar = LibraryManager.downloadMaven(
                        groupF.getText().trim(),
                        artifactF.getText().trim(),
                        versionF.getText().trim());
                if (jar != null) {
                    try {
                        Path libDir = IDE.project.root.resolve("lib");
                        Files.createDirectories(libDir);
                        Path dst = libDir.resolve(jar.getFileName().toString());
                        if (!Files.exists(dst)) Files.copy(jar, dst);
                        PackageRepository.updateProjectLibraries(IDE.project);
                        SwingUtilities.invokeLater(() -> {
                            statusL.setText("Downloaded: " + dst.getFileName());
                            IDE.appendConsole("Maven: added " + dst.getFileName());
                        });
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() ->
                                statusL.setText("Copy failed: " + Log.msg(ex)));
                    }
                } else {
                    SwingUtilities.invokeLater(() ->
                            statusL.setText("Download failed."));
                }
            }, "jefb-pm-maven").start();
        });

        gbc.gridy = 6;
        root.add(downloadBtn, gbc);
        gbc.gridy = 7;
        root.add(statusL, gbc);

        return root;
    }

    static class PackageRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof PackageRepository.Item it) {
                setText(it.toString());
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                com.formdev.flatlaf.extras.FlatSVGIcon icon = IdeIcons.get("package", 20);
                if (icon != null) setIcon(icon);
            }
            return this;
        }
    }

    private PackageManagerDialog() {}
}