package ir.IrAutoX.JEFB;

import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public final class DecompilerUtil {

    public static String decompile(Path input) throws Exception {
        Path tempDir = Files.createTempDirectory("jefb-decomp-");
        try {
            Decompiler decompiler = new Decompiler.Builder()
                    .inputs(input.toFile())
                    .output(new org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver(tempDir.toFile()))
                    .option(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, true)
                    .build();
            decompiler.decompile();
            StringBuilder sb = new StringBuilder();
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a)
                        throws IOException {
                    if (f.toString().endsWith(".java")) {
                        sb.append("// ===== ").append(tempDir.relativize(f)).append(" =====\n\n");
                        sb.append(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
                        sb.append("\n\n");
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return sb.toString();
        } finally {
            IO.deleteRecursively(tempDir);
        }
    }

    private DecompilerUtil() {}
}