package ir.IrAutoX.JEFB;

public final class Diag {

    public enum Sev { ERROR, WARNING, INFO }

    public final Sev sev;
    public final String file;
    public final long line, col;
    public final String msg;

    public Diag(Sev s, String f, long l, long c, String m) {
        sev = s; file = f; line = l; col = c; msg = m;
    }
}