package it.topperia.game.ui;

/** Small, deterministic motion primitives shared by the canvas interactions. */
public final class Motion {
    private Motion() {}

    public static float clamp(float value) {
        if (!(value > 0)) return 0;
        return Math.min(1, value);
    }

    public static float ease(float progress) {
        float remaining = 1 - clamp(progress);
        return 1 - remaining * remaining * remaining;
    }

    public static float progress(long time, long start, long duration) {
        if (duration <= 0) return 1;
        if (time <= start) return 0;
        long elapsed = time - start;
        if (elapsed < 0 || elapsed >= duration) return 1;
        return (float) ((double) elapsed / duration);
    }

    public static float lerp(float from, float to, float progress) {
        float amount = clamp(progress);
        return (float) ((1 - (double) amount) * from + (double) amount * to);
    }
}
