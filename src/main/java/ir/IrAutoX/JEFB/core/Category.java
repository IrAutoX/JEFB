package ir.IrAutoX.JEFB.core;

import java.util.*;

/**
 * Error category, used to group diagnostics and select documentation links.
 */
public enum Category {

    /** Grammar / lexical problems. */
    SYNTAX("syntax", "java-syntax"),

    /** Type errors, unreachable code, bad overrides. */
    SEMANTIC("semantic", "java-semantics"),

    /** Missing classes, packages, or libraries on the classpath. */
    DEPENDENCY("dependency", "dependencies"),

    /** Plugin load / sandbox / hook failures. */
    PLUGIN("plugin", "plugins"),

    /** File system problems: unreadable, missing, locked. */
    IO("io", "troubleshooting"),

    /** Failures while running the built program. */
    RUNTIME("runtime", "troubleshooting"),

    /** Build configuration problems (jefb.conf, package rules). */
    CONFIG("config", "configuration"),

    /** Anything uncategorized. */
    GENERAL("general", "troubleshooting");

    private final String id;
    private final String docPage;

    Category(String id, String docPage) {
        this.id = id;
        this.docPage = docPage;
    }

    public String id() { return id; }

    /** Online documentation anchor for this category. */
    public String docUrl() { return "https://github.com/IrAutoX/JEFB/docs/" + docPage; }

    /** Best-effort category inference from a compiler message. */
    public static Category infer(String message) {
        if (message == null) return GENERAL;
        String m = message.toLowerCase();
        if (m.contains("expected") || m.contains("illegal") || m.contains("unclosed")
                || m.contains("reached end of file") || m.contains("not a statement")
                || m.contains("';' expected") || m.contains("illegal start"))
            return SYNTAX;
        if (m.contains("cannot find symbol") || m.contains("incompatible types")
                || m.contains("already defined") || m.contains("not a statement")
                || m.contains("abstract method") || m.contains("does not override"))
            return SEMANTIC;
        if (m.contains("package") && m.contains("does not exist")
                || m.contains("cannot access") || m.contains("class file not found")
                || m.contains("no suitable constructor") && m.contains("import"))
            return DEPENDENCY;
        if (m.contains("permission denied") || m.contains("access is denied")
                || m.contains("file not found") || m.contains("no such file"))
            return IO;
        return GENERAL;
    }
}
