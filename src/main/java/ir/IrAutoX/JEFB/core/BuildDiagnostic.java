package ir.IrAutoX.JEFB.core;

import ir.IrAutoX.JEFB.common.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single structured build diagnostic.
 *
 * Carries everything needed for both human console output (caret rendering,
 * suggested fixes) and machine consumption (the IDE parses the JSON form and
 * colors it accordingly).
 */
public final class BuildDiagnostic {

    private final Severity severity;
    private final Category category;
    private final String code;          // stable code such as JEFB-E1001
    private final String file;          // project-relative path, "" when n/a
    private final long line;            // 1-based, 0 when unknown
    private final long column;          // 1-based, 0 when unknown
    private final String message;       // raw problem
    private final String explanation;   // human-readable "what went wrong"
    private final String fix;           // suggested fix, "" when none
    private final String docUrl;        // related documentation, "" when none
    private final String sourceLine;    // offending source text, "" when unknown

    public BuildDiagnostic(Severity severity, Category category, String code,
                           String file, long line, long column, String message,
                           String explanation, String fix, String docUrl,
                           String sourceLine) {
        this.severity = severity == null ? Severity.ERROR : severity;
        this.category = category == null ? Category.GENERAL : category;
        this.code = code == null ? "" : code;
        this.file = file == null ? "" : file;
        this.line = line;
        this.column = column;
        this.message = message == null ? "" : message;
        this.explanation = explanation == null ? "" : explanation;
        this.fix = fix == null ? "" : fix;
        this.docUrl = docUrl == null ? "" : docUrl;
        this.sourceLine = sourceLine == null ? "" : sourceLine;
    }

    public Severity severity()   { return severity; }
    public Category category()   { return category; }
    public String code()         { return code; }
    public String file()         { return file; }
    public long line()           { return line; }
    public long column()         { return column; }
    public String message()      { return message; }
    public String explanation()  { return explanation; }
    public String fix()          { return fix; }
    public String docUrl()       { return docUrl; }
    public String sourceLine()   { return sourceLine; }

    /** Stable error/warning code derived from category + severity. */
    public static String makeCode(Severity sev, Category cat) {
        String letter = sev == Severity.ERROR ? "E" : sev == Severity.WARNING ? "W" : "I";
        return "JEFB-" + letter + cat.id().substring(0, 1).toUpperCase()
                + cat.id().substring(1) + "-" + (cat.ordinal() * 100 + sev.ordinal());
    }

    /** Machine-readable JSON form parsed by the IDE console. */
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", severity.lower());
        m.put("category", category.id());
        m.put("code", code);
        m.put("file", file);
        m.put("line", line);
        m.put("column", column);
        m.put("message", message);
        if (!explanation.isBlank()) m.put("explanation", explanation);
        if (!fix.isBlank()) m.put("fix", fix);
        if (!docUrl.isBlank()) m.put("doc", docUrl);
        if (!sourceLine.isBlank()) m.put("sourceLine", sourceLine);
        return m;
    }

    /** JSON string for a whole diagnostic list (the IDE wires protocol). */
    public static String toJsonList(Iterable<BuildDiagnostic> diags) {
        java.util.List<Object> arr = new java.util.ArrayList<>();
        for (BuildDiagnostic d : diags) arr.add(d.toJson());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("jefbDiagnostics", 1);
        root.put("items", arr);
        return Json.write(root);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity.lower()).append(':');
        if (!category.id().equals("general")) sb.append(category.id()).append(':');
        sb.append(' ').append(message);
        if (!file.isBlank()) {
            sb.append("  [").append(file);
            if (line > 0) sb.append(':').append(line);
            sb.append(']');
        }
        return sb.toString();
    }
}
