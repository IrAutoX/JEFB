package ir.IrAutoX.JEFB;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IdeIcons {

    private static final ImageIcon EMPTY = new ImageIcon(
            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));

    private static final Map<String, FlatSVGIcon> SVG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> CUSTOM_ICON_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> OS_ICON_CACHE = new ConcurrentHashMap<>();

    private static final java.util.Set<String> SVG_MISS = ConcurrentHashMap.newKeySet();

    public static FlatSVGIcon get(String name, int size) {
        String key = name + ":" + size;
        if (SVG_MISS.contains(key)) return null;
        FlatSVGIcon cached = SVG_CACHE.get(key);
        if (cached != null) return cached;
        try {
            java.net.URL url = JBuild.class.getResource("/icons/" + name + ".svg");
            if (url == null) {
                SVG_MISS.add(key);
                return null;
            }
            FlatSVGIcon icon = new FlatSVGIcon(url);
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> {
                Color fg = UIManager.getColor("Label.foreground");
                return fg != null ? fg : Color.WHITE;
            }));
            SVG_CACHE.put(key, icon);
            return icon;
        } catch (Throwable e) {
            SVG_MISS.add(key);
            return null;
        }
    }

    public static ImageIcon getCustomIcon(Path path) {
        if (path == null) return null;
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = fileName.substring(dot).toLowerCase();
        String mapped = mapExtension(ext);
        if (mapped == null) return null;

        ImageIcon cached = CUSTOM_ICON_CACHE.get(mapped);
        if (cached != null) return cached == EMPTY ? null : cached;

        try {
            java.net.URL url = JBuild.class.getResource("/icons/" + mapped);
            if (url == null) {
                CUSTOM_ICON_CACHE.put(mapped, EMPTY);
                return null;
            }
            ImageIcon icon;
            if (mapped.endsWith(".ico")) {
                try (InputStream is = url.openStream()) {
                    BufferedImage img = ImageIO.read(is);
                    if (img == null) {
                        CUSTOM_ICON_CACHE.put(mapped, EMPTY);
                        return null;
                    }
                    Image scaled = img.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                    icon = new ImageIcon(scaled);
                }
            } else {
                Image img = new ImageIcon(url).getImage();
                Image scaled = img.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                icon = new ImageIcon(scaled);
            }
            CUSTOM_ICON_CACHE.put(mapped, icon);
            return icon;
        } catch (Throwable e) {
            CUSTOM_ICON_CACHE.put(mapped, EMPTY);
            return null;
        }
    }

    private static String mapExtension(String ext) {
        switch (ext) {
            case ".java": return "ja.ico";
            case ".jar":  return "java.png";
            case ".class": return "default.ico";
            case ".conf":
            case ".properties":
            case ".xml":
            case ".json":
            case ".gradle":
                return "config.ico";
            default: return null;
        }
    }

    public static ImageIcon getSystemIcon(Path path) {
        if (path == null) return null;
        boolean isDir;
        try {
            isDir = Files.isDirectory(path);
        } catch (Exception e) {
            return null;
        }

        if (!isDir) {
            try {
                ImageIcon custom = getCustomIcon(path);
                if (custom != null) return custom;
            } catch (Throwable ignored) {}
        }

        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "<noext>" : fileName.substring(dot).toLowerCase();
        String cacheKey = isDir ? "dir" : "ext:" + ext;

        ImageIcon cached = OS_ICON_CACHE.get(cacheKey);
        if (cached != null) return cached == EMPTY ? null : cached;

        try {
            javax.swing.filechooser.FileSystemView fsv =
                    javax.swing.filechooser.FileSystemView.getFileSystemView();
            Icon icon = fsv.getSystemIcon(path.toFile());
            if (icon == null) {
                OS_ICON_CACHE.put(cacheKey, EMPTY);
                return null;
            }
            int w = icon.getIconWidth(), h = icon.getIconHeight();
            if (w <= 0 || h <= 0) {
                OS_ICON_CACHE.put(cacheKey, EMPTY);
                return null;
            }
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            icon.paintIcon(null, g2, 0, 0);
            g2.dispose();
            ImageIcon out = new ImageIcon(img);
            OS_ICON_CACHE.put(cacheKey, out);
            return out;
        } catch (Throwable e) {
            OS_ICON_CACHE.put(cacheKey, EMPTY);
            return null;
        }
    }

    public static void clearCache() {
        SVG_CACHE.clear();
        OS_ICON_CACHE.clear();
        CUSTOM_ICON_CACHE.clear();
        SVG_MISS.clear();
    }

    private IdeIcons() {}
}