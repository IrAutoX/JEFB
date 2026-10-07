package ir.IrAutoX.JEFB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class TrustedPublisherDialog {

    public static void show() {
        JDialog d = new JDialog(IDE.frame, "Trusted Publishers", false);
        d.setSize(700, 480);
        d.setLocationRelativeTo(IDE.frame);

        DefaultListModel<TrustedPublisher.Info> model = new DefaultListModel<>();
        JList<TrustedPublisher.Info> list = new JList<>(model);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        list.setCellRenderer(new TrustedRenderer());

        JLabel status = new JLabel(" ");

        Runnable reload = () -> {
            model.clear();
            List<TrustedPublisher.Info> infos = TrustedPublisher.listTrusted();
            for (TrustedPublisher.Info i : infos) model.addElement(i);
            status.setText(infos.size() + " trusted publisher(s)");
        };

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addKeyBtn = new JButton("Add Public Key...");
        JButton trustJarBtn = new JButton("Trust JAR...");
        JButton removeBtn = new JButton("Remove");
        JButton refreshBtn = new JButton("Refresh");
        top.add(addKeyBtn);
        top.add(trustJarBtn);
        top.add(removeBtn);
        top.add(refreshBtn);

        addKeyBtn.addActionListener(e -> {
            JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
            form.setBorder(new EmptyBorder(10, 10, 10, 10));
            JTextField companyF = new JTextField();
            JTextArea keyArea = new JTextArea(6, 50);
            keyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            form.add(new JLabel("Company:"));
            form.add(companyF);
            form.add(new JLabel("Public key (base64):"));
            form.add(new JScrollPane(keyArea));
            if (JOptionPane.showConfirmDialog(IDE.frame, form,
                    "Add Trusted Public Key",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            String company = companyF.getText().trim();
            String key = keyArea.getText().trim();
            if (company.isEmpty() || key.isEmpty()) return;
            try {
                TrustedPublisher.trustKey(company, key);
                reload.run();
                IDE.appendConsole("Trusted key added: " + company);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(IDE.frame,
                        "Add failed: " + Log.msg(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        trustJarBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(IDE.frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    TrustedPublisher.trustJar(fc.getSelectedFile().toPath());
                    reload.run();
                    IDE.appendConsole("Trusted JAR's publisher");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(IDE.frame,
                            "Trust failed: " + Log.msg(ex),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        removeBtn.addActionListener(e -> {
            TrustedPublisher.Info info = list.getSelectedValue();
            if (info == null) return;
            try {
                TrustedPublisher.untrustKey(info.pubKeyB64);
                reload.run();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(IDE.frame,
                        "Remove failed: " + Log.msg(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        refreshBtn.addActionListener(e -> reload.run());

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.setVisible(true);
        reload.run();
    }

    static class TrustedRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof TrustedPublisher.Info i) {
                setText("<html><b>" + i.company + "</b><br><font color='#4CAF50' size='3'>"
                        + i.pubKeySha256.substring(0, 32) + "...</font></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                com.formdev.flatlaf.extras.FlatSVGIcon icon = IdeIcons.get("verify", 24);
                if (icon != null) setIcon(icon);
            }
            return this;
        }
    }

    private TrustedPublisherDialog() {}
}