package ir.IrAutoX.JEFB;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public final class IO {

    public static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        Files.walkFileTree(p, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a)
                    throws IOException { Files.deleteIfExists(f); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException e)
                    throws IOException { if (e != null) throw e;
                        Files.deleteIfExists(d); return FileVisitResult.CONTINUE; }
        });
    }

    public static List<Path> listJava(Path root) throws IOException {
        List<Path> r = new ArrayList<>();
        if (!Files.isDirectory(root)) return r;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (f.toString().endsWith(".java")) r.add(f);
                return FileVisitResult.CONTINUE;
            }
        });
        return r;
    }

    public static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) return;
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a)
                    throws IOException {
                Files.createDirectories(to.resolve(from.relativize(d).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a)
                    throws IOException {
                Path t = to.resolve(from.relativize(f).toString());
                Files.createDirectories(t.getParent());
                Files.copy(f, t, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public static void write(Path p, String s) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, s.getBytes(StandardCharsets.UTF_8));
    }

    public static String rel(Path root, Path p) {
        try {
            return p.startsWith(root)
                ? root.relativize(p).toString().replace(File.separatorChar, '/')
                : p.toString();
        } catch (Exception e) { return p.toString(); }
    }

    private IO() {}
}