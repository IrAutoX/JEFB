package ir.IrAutoX.JEFB.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight build profiler. Phases are recorded with wall time and printed
 * as a sorted breakdown at the end of the build so users can see where time
 * is spent.
 */
public final class BuildProfiler {

    /** One recorded phase. */
    public record Phase(String name, long millis) {}

    private final Map<String, Long> open = new LinkedHashMap<>();
    private final List<Phase> phases = new ArrayList<>();
    private final long startNanos = System.nanoTime();
    private boolean enabled;

    public BuildProfiler(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    /** Start timing a named phase. Nested/timed phases are fine. */
    public void begin(String phase) {
        if (enabled) open.putIfAbsent(phase, System.nanoTime());
    }

    /** Stop timing a named phase started with {@link #begin}. */
    public void end(String phase) {
        Long t0 = open.remove(phase);
        if (t0 != null) phases.add(new Phase(phase, (System.nanoTime() - t0) / 1_000_000));
    }

    /** Time a whole block: {@code p.time("scan", () -> ...)}. */
    public void time(String phase, Runnable body) {
        begin(phase);
        try {
            body.run();
        } finally {
            end(phase);
        }
    }

    /** Total wall time of the profiled build in milliseconds. */
    public long totalMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Render the phase breakdown, longest first. */
    public String report() {
        StringBuilder sb = new StringBuilder("Build profile (total "
                + totalMillis() + " ms):\n");
        List<Phase> sorted = new ArrayList<>(phases);
        sorted.sort((a, b) -> Long.compare(b.millis(), a.millis()));
        long max = sorted.stream().mapToLong(Phase::millis).max().orElse(1);
        for (Phase p : sorted) {
            int bars = (int) Math.max(1, Math.round(p.millis() * 20.0 / max));
            sb.append(String.format("  %-24s %6d ms  %s%n",
                    p.name(), p.millis(), "#".repeat(bars)));
        }
        if (sorted.isEmpty()) sb.append("  (no phases recorded)\n");
        return sb.toString();
    }
}
