package ir.IrAutoX.JEFB;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class FilePreview {

    public static String detectFormat(Path f) {
        try {
            byte[] h = new byte[16];
            try (InputStream is = Files.newInputStream(f)) {
                int n = is.read(h);
                if (n < 4) return null;
            }
            if (h[0] == (byte)0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G')
                return "png";
            if (h[0] == (byte)0xFF && h[1] == (byte)0xD8)
                return "jpg";
            if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F')
                return "gif";
            if (h[0] == 'B' && h[1] == 'M')
                return "bmp";
            if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F')
                return "webp";
            String s = new String(h, StandardCharsets.UTF_8).trim();
            if (s.startsWith("<?xml") || s.startsWith("<svg"))
                return "svg";
            if (s.startsWith("{"))
                return "json";
            if (s.startsWith("PK"))
                return "zip";
            if (h[0] == (byte)0xCA && h[1] == (byte)0xFE && h[2] == (byte)0xBA)
                return "class";
            return null;
        } catch (IOException e) { return null; }
    }

    public static boolean isImage(Path f) {
        String fmt = detectFormat(f);
        return "png".equals(fmt) || "jpg".equals(fmt) || "gif".equals(fmt)
                || "bmp".equals(fmt) || "webp".equals(fmt);
    }

    public static boolean isSvg(Path f) {
        return "svg".equals(detectFormat(f));
    }

    public static JComponent imagePreview(Path f) {
        try {
            if (isSvg(f)) {
                byte[] data = Files.readAllBytes(f);
                FlatSVGIcon icon = new FlatSVGIcon(new ByteArrayInputStream(data));
                JLabel lbl = new JLabel(icon);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                JPanel p = new JPanel(new BorderLayout());
                p.setBackground(new Color(0x2b2b2b));
                p.add(lbl, BorderLayout.CENTER);
                return p;
            }
            Image img = new ImageIcon(Files.readAllBytes(f)).getImage();
            JLabel lbl = new JLabel(new ImageIcon(img));
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(new Color(0x2b2b2b));
            p.add(new JScrollPane(lbl), BorderLayout.CENTER);
            return p;
        } catch (Exception e) {
            return new JLabel("Cannot preview: " + e.getMessage());
        }
    }

    private FilePreview() {}
}