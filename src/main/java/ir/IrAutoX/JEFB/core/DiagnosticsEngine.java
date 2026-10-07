package ir.IrAutoX.JEFB.core;

import ir.IrAutoX.JEFB.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * Turns raw javac messages into rich, human-oriented diagnostics:
 * <ul>
 *   <li>exact file:line:column,</li>
 *   <li>the offending source line with a {@code ^} caret under the column,</li>
 *   <li>an explanation of what went wrong,</li>
 *   <li>a suggested fix when we can infer one,</li>
 *   <li>a documentation link,</li>
 *   <li>a structured JSON dump for the IDE.</li>
 * </ul>
 */
public final class DiagnosticsEngine {

    /** Consumer for the JSON block (IDE console listens to this). */
    public static final Consumer<String> JSON_SINK_DEFAULT = json ->
            System.out.println("__JEFB_DIAG__" + json);

    private static volatile Consumer<String> jsonSink = JSON_SINK_DEFAULT;

    /** Register a different JSON sink (used by the IDE to intercept). */
    public static void setJsonSink(Consumer<String> sink) {
        jsonSink = sink == null ? JSON_SINK_DEFAULT : sink;
    }

    private final Path projectRoot;
    private final List<BuildDiagnostic> diags = new ArrayList<>();
    private final Map<String, List<String>> sourceCache = new HashMap<>();

    public DiagnosticsEngine(Path projectRoot) {
        this.projectRoot = projectRoot == null ? Paths.get(".") : projectRoot;
    }

    /** All diagnostics collected so far. */
    public List<BuildDiagnostic> all() {
        return Collections.unmodifiableList(diags);
    }

    public int errorCount()  { return count(Severity.ERROR); }
    public int warnCount()   { return count(Severity.WARNING); }

    private int count(Severity s) {
        int n = 0;
        for (BuildDiagnostic d : diags) if (d.severity() == s) n++;
        return n;
    }

    /** Add a fully-built diagnostic. */
    public void add(BuildDiagnostic d) {
        diags.add(d);
    }

    /** Convert a javac-style message ("file:line: error: text") into diagnostics. */
    public void addRawMessage(String raw) {
        // javac text form: <file>:<line>: <severity>: <message>
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.+?):(\\d+):\\s+(error|warning|info):\\s+(.*)$")
                .matcher(raw);
        if (!m.matches()) {
            diags.add(new BuildDiagnostic(Severity.INFO, Category.GENERAL,
                    BuildDiagnostic.makeCode(Severity.INFO, Category.GENERAL),
                    "", 0, 0, raw, "", "", "", ""));
            return;
        }
        Severity sev = switch (m.group(3)) {
            case "error" -> Severity.ERROR;
            case "warning" -> Severity.WARNING;
            default -> Severity.INFO;
        };
        addLocation(sev, m.group(1), Long.parseLong(m.group(2)), 0, m.group(4));
    }

    /**
     * Build a rich diagnostic for a location, enriching with source context,
     * explanation and suggested fix.
     */
    public void addLocation(Severity sev, String file, long line, long col, String message) {
        Category cat = Category.infer(message);
        String explanation = explain(message);
        String fix = suggest(message);
        String src = readSourceLine(file, line);
        BuildDiagnostic d = new BuildDiagnostic(sev, cat,
                BuildDiagnostic.makeCode(sev, cat),
                file, line, col, message, explanation, fix,
                cat.docUrl(), src);
        diags.add(d);
    }

    /** Print all diagnostics in the pretty console format. */
    public void printAll() {
        for (BuildDiagnostic d : diags) printOne(d);
    }

    /** Print one diagnostic with caret and hints. */
    public void printOne(BuildDiagnostic d) {
        Log.raw("");
        Log.raw("  " + location(d));
        Log.raw("  " + d.severity().lower() + " [" + d.code() + "] "
                + d.message());
        if (!d.sourceLine().isBlank()) {
            Log.raw("      " + d.sourceLine());
            int col = (int) Math.max(1, d.column());
            int pad = 6 + Math.min(col - 1, d.sourceLine().length());
            StringBuilder caret = new StringBuilder();
            for (int i = 0; i < pad; i++) caret.append(' ');
            caret.append('^');
            Log.raw(caret.toString());
        }
        if (!d.explanation().isBlank()) Log.raw("  why:     " + d.explanation());
        if (!d.fix().isBlank())         Log.raw("  fix:     " + d.fix());
        if (!d.docUrl().isBlank())      Log.raw("  docs:    " + d.docUrl());
    }

    private String location(BuildDiagnostic d) {
        if (d.file().isBlank()) return "(no location)";
        StringBuilder sb = new StringBuilder(d.file());
        if (d.line() > 0) {
            sb.append(':').append(d.line());
            if (d.column() > 0) sb.append(':').append(d.column());
        }
        return sb.toString();
    }

    /** Emit the JSON diagnostics block consumed by the IDE. */
    public void emitJson() {
        try {
            jsonSink.accept(BuildDiagnostic.toJsonList(diags));
        } catch (Throwable ignored) {
            // JSON emission must never break the build
        }
    }

    /** Write diagnostics JSON to build/diagnostics.json. Returns the path or null. */
    public Path writeJsonFile(Path buildDir) {
        try {
            Files.createDirectories(buildDir);
            Path out = buildDir.resolve("diagnostics.json");
            Files.writeString(out, BuildDiagnostic.toJsonList(diags), StandardCharsets.UTF_8);
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------- enrichment

    private String readSourceLine(String file, long line) {
        if (file == null || file.isBlank() || line <= 0) return "";
        try {
            List<String> lines = sourceCache.computeIfAbsent(file, f -> {
                Path p = projectRoot.resolve(f);
                if (!Files.isRegularFile(p)) p = Paths.get(f);
                if (!Files.isRegularFile(p)) return List.of();
                try {
                    return Files.readAllLines(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return List.of();
                }
            });
            if (line <= lines.size()) return lines.get((int) line - 1).stripTrailing();
        } catch (Exception ignored) {}
        return "";
    }

    /** Human-readable explanation for common compiler messages. */
    static String explain(String msg) {
        if (msg == null) return "";
        String m = msg.toLowerCase();
        if (m.contains("cannot find symbol"))
            return "A name used here is not defined in this scope. It may be misspelled, "
                    + "not imported, or defined in a different package.";
        if (m.contains("package") && m.contains("does not exist"))
            return "The imported package is not on the classpath. Add the library that "
                    + "provides it to lib/ or compiler.classpath.";
        if (m.contains("incompatible types"))
            return "The expression's type does not match the target type. An explicit "
                    + "conversion or a different variable type is needed.";
        if (m.contains("unreported exception"))
            return "This call can throw a checked exception which must be caught or declared.";
        if (m.contains("missing return statement"))
            return "Not all code paths in this method return a value.";
        if (m.contains("unclosed string literal"))
            return "A string literal was opened but never closed with a quote character.";
        if (m.contains("reached end of file while parsing"))
            return "A block ({...}) was left open; the file ended before it was closed.";
        if (m.contains("is not abstract and does not override"))
            return "A class claims to implement an interface but a required method is missing.";
        if (m.contains("method does not override"))
            return "@Override was applied but no supertype method matches this signature.";
        if (m.contains("already defined"))
            return "A variable, method or class with this name already exists in this scope.";
        if (m.contains("arrayindexoutofbound") || m.contains("out of bounds"))
            return "An index was used outside the valid range of an array or list.";
        if (m.contains("nullpointer"))
            return "A reference used here was null at runtime.";
        if (m.contains("access is denied") || m.contains("permission denied"))
            return "The operating system refused access; the file may be locked or "
                    + "read-only, or the sandbox denied the operation.";
        return "";
    }

    /** Suggested fix for common compiler messages. */
    static String suggest(String msg) {
        if (msg == null) return "";
        String m = msg.toLowerCase();
        if (m.contains("cannot find symbol")) {
            java.util.regex.Matcher sym = java.util.regex.Pattern
                    .compile("symbol:\\s*(\\w+)\\s+([\\w.]+)")
                    .matcher(msg);
            if (sym.find()) {
                String kind = sym.group(1);
                String name = sym.group(2);
                if (kind.equals("class"))
                    return "Check the import section for '" + name + "', or fully qualify it.";
                if (kind.equals("variable"))
                    return "Declare '" + name + "' before use, or fix the spelling.";
                if (kind.equals("method"))
                    return "Verify the method '" + name + "' exists with matching parameters.";
            }
            return "Check spelling, imports, and that the symbol exists in a library on the classpath.";
        }
        if (m.contains("package") && m.contains("does not exist"))
            return "Add the providing JAR to lib/ (or compiler.classpath) and rebuild.";
        if (m.contains("incompatible types"))
            return "Add an explicit cast, or change the variable type to match the expression.";
        if (m.contains("unreported exception"))
            return "Wrap the call in try/catch, or declare 'throws' on the method.";
        if (m.contains("missing return statement"))
            return "Add a return statement on every path, or change the return type to void.";
        if (m.contains("unclosed string literal"))
            return "Close the string with a matching '\"'.";
        if (m.contains("reached end of file while parsing"))
            return "Balance the braces; every '{' needs a matching '}'.";
        if (m.contains("already defined"))
            return "Rename one of the two declarations.";
        return "";
    }
}
