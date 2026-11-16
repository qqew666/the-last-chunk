package dev.mirnaubyl;
/** Server ticks, paused while waiting for a choice or while nobody is online. */
public final class RoundClock {
    public static final int INTERVAL = 5 * 60 * 20;
    private int elapsed;
    public RoundClock(int elapsed) { this.elapsed = Math.max(0, Math.min(INTERVAL, elapsed)); }
    public boolean tick() { if (elapsed < INTERVAL) elapsed++; return ready(); }
    public boolean ready() { return elapsed >= INTERVAL; }
    public int elapsed() { return elapsed; }
    public void reset() { elapsed = 0; }
}
