package ir.IrAutoX.JEFB;

public final class Log {

    static boolean debug = false;

    static void init(boolean d) { debug = d; }
    static void raw(String s)   { System.out.println(s); }
    static void ok(String s)    { System.out.println("[OK] " + s); }
    static void warn(String s)  { System.err.println("[WARN] " + s); }
    static void error(String s) { System.err.println("ERROR: " + s); }
    static void debug(String s) { if (debug) System.out.println("[debug] " + s); }

    static String msg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    private Log() {}
}