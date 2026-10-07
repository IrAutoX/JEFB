package ir.IrAutoX.JEFB.core;

/**
 * Severity of a {@link BuildDiagnostic}.
 */
public enum Severity {
    ERROR("error"), WARNING("warning"), INFO("info");

    private final String lower;

    Severity(String lower) { this.lower = lower; }

    /** Lowercase name, as used in JSON output and console colors. */
    public String lower() { return lower; }
}
