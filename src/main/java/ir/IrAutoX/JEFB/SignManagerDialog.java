package ir.IrAutoX.JEFB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SignManagerDialog {

    public static void show() {
        if (IDE.project == null) {
            JOptionPane.showMessageDialog(IDE.frame, "No project loaded.",
                    "Sign Manager", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        show(IDE.project);
    }

    public static void showStandalone() {
        show((Project) null);
    }

    static void show(Project p) {
        SignMeta existing = null;
        if (p != null) {
            try { existing = SignUtil.loadMeta(p); }
            catch (IOException ignored) {}
        }

        JTextField companyF = new JTextField(existing != null ? existing.company : "", 30);
        JTextField authorF  = new JTextField(existing != null ? existing.author
                : JBuild.AUTHOR + " / " + JBuild.DEVELOPER, 30);
        JTextField issuedF  = new JTextField(existing != null ? existing.issued
                : LocalDate.now().format(DateTimeFormatter.ISO_DATE), 30);
        JTextField expiresF = new JTextField(existing != null ? existing.expires
                : LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_DATE), 30);
        JTextField licenseF = new JTextField(existing != null ? existing.license
                : "Proprietary", 30);
        JTextArea status = new JTextArea(8, 50);
        status.setEditable(false);
        status.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        updateStatus(status, existing);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;
        addRow(form, gbc, y++, "Company:", companyF);
        addRow(form, gbc, y++, "Author:", authorF);
        addRow(form, gbc, y++, "Issued (YYYY-MM-DD):", issuedF);
        addRow(form, gbc, y++, "Expires (YYYY-MM-DD):", expiresF);
        addRow(form, gbc, y++, "License:", licenseF);
        gbc.gridx = 0; gbc.gridy = y; gbc.gridwidth = 2; gbc.weightx = 1;
        form.add(new JScrollPane(status), gbc);

        JButton signBtn = new JButton("Sign Project");
        signBtn.addActionListener(e -> {
            if (p == null) return;
            try {
                String company = companyF.getText().trim();
                String author = authorF.getText().trim();
                String issued = issuedF.getText().trim();
                String expires = expiresF.getText().trim();
                String license = licenseF.getText().trim();
                if (company.isEmpty() || author.isEmpty()) return;

                String pHash = SignUtil.projectHash(p);
                SignMeta m = new SignMeta(company, author, issued, expires, license, "", pHash);
                String sig = SignUtil.buildSignature(m);
                m = new SignMeta(company, author, issued, expires, license, sig, pHash);
                SignUtil.saveMeta(p, m);

                Map<String, String> sigs = new LinkedHashMap<>();
                Path classes = p.classesPath();
                if (Files.isDirectory(classes)) {
                    Files.walkFileTree(classes, new java.nio.file.SimpleFileVisitor<>() {
                        @Override public java.nio.file.FileVisitResult visitFile(
                                Path f, java.nio.file.attribute.BasicFileAttributes a) {
                            try { sigs.put(IO.rel(classes, f), Crypto.sha256(f)); }
                            catch (IOException ignored) {}
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    });
                }
                SignUtil.saveSignatures(p, sigs);
                p.updateSignature(sig);
                try { IDE.project = Project.load(p.root); }
                catch (BuildException ignored) {}
                updateStatus(status, m);
                IDE.appendConsole("Signed " + sigs.size() + " classes for " + company);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(IDE.frame, "Sign failed: " + Log.msg(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton verifyBtn = new JButton("Verify");
        verifyBtn.addActionListener(e -> {
            if (p == null) return;
            try {
                SignMeta m = SignUtil.loadMeta(p);
                if (m == null) return;
                updateStatus(status, m);
                boolean v = SignUtil.verifyMeta(m);
                boolean exp = SignUtil.isExpired(m);
                boolean cls = SignUtil.verify(p);
                JOptionPane.showMessageDialog(IDE.frame,
                        "Metadata: " + (v ? "OK" : "INVALID") + "\nExpired: " + exp
                                + "\nClasses: " + (cls ? "OK" : "MISMATCH"),
                        "Verify", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(IDE.frame, "Verify failed: " + Log.msg(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            if (p == null) return;
            try {
                Files.deleteIfExists(p.root.resolve(SignUtil.SIGN_FILE));
                Files.deleteIfExists(p.root.resolve(SignUtil.META_FILE));
                p.updateSignature("");
                try { IDE.project = Project.load(p.root); } catch (BuildException ignored) {}
                updateStatus(status, null);
            } catch (Exception ignored) {}
        });

        JButton closeBtn = new JButton("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(signBtn);
        buttons.add(verifyBtn);
        buttons.add(clearBtn);
        buttons.add(closeBtn);

        JPanel root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(IDE.frame,
                p == null ? "Sign Manager (no project)" : "Sign Manager: " + p.name, false);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(IDE.frame);
        closeBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    static void addRow(JPanel p, GridBagConstraints gbc, int y, String label, JComponent field) {
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
        p.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(field, gbc);
    }

    static void updateStatus(JTextArea area, SignMeta m) {
        if (m == null) { area.setText("No sign metadata present.\n"); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("Company : ").append(m.company).append("\n");
        sb.append("Author  : ").append(m.author).append("\n");
        sb.append("Issued  : ").append(m.issued).append("\n");
        sb.append("Expires : ").append(m.expires).append("\n");
        sb.append("License : ").append(m.license).append("\n");
        sb.append("Hash    : ").append(m.projectHash).append("\n");
        sb.append("Sig     : ").append(m.signature).append("\n");
        try {
            sb.append("Valid   : ").append(SignUtil.verifyMeta(m)).append("\n");
            sb.append("Expired : ").append(SignUtil.isExpired(m)).append("\n");
        } catch (IOException ignored) {}
        area.setText(sb.toString());
    }

    private SignManagerDialog() {}
}