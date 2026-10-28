import it.topperia.game.ui.Motion;

/** Timing and reversal checks without an Android runtime or frame-rate assumptions. */
public final class MotionTest {
    private static int checks;

    public static void main(String[] args) {
        check(Motion.clamp(-1) == 0 && Motion.clamp(2) == 1, "clamp overshoots");
        check(Motion.clamp(0) == 0 && Motion.clamp(1) == 1, "preserve endpoints");
        check(Motion.clamp(.5f) == .5f, "preserve midpoint");
        check(Motion.clamp(Float.NaN) == 0, "invalid progress starts safely");
        check(Motion.clamp(Float.NEGATIVE_INFINITY) == 0
                && Motion.clamp(Float.POSITIVE_INFINITY) == 1, "clamp infinite progress");
        check(Motion.ease(0) == 0 && Motion.ease(1) == 1, "ease reaches exact endpoints");
        check(Motion.ease(.5f) == .875f, "cubic ease-out midpoint");
        check(Motion.ease(-1) == 0 && Motion.ease(2) == 1, "ease cannot overshoot");

        check(Motion.progress(900, 1000, 400) == 0, "future start remains at zero");
        check(Motion.progress(1000, 1000, 400) == 0, "start is exact");
        check(Motion.progress(1200, 1000, 400) == .5f, "half duration");
        check(Motion.progress(1400, 1000, 400) == 1, "end is exact");
        check(Motion.progress(1800, 1000, 400) == 1, "finished motion stays finished");
        check(Motion.progress(1000, 1000, 0) == 1
                && Motion.progress(1000, 1000, -1) == 1, "zero duration completes immediately");
        check(Motion.progress(Long.MAX_VALUE, Long.MIN_VALUE, 400) == 1
                && Motion.progress(Long.MIN_VALUE, Long.MAX_VALUE, 400) == 0,
                "extreme timestamps cannot overflow");
        check(Motion.progress(Long.MAX_VALUE - 200, Long.MAX_VALUE - 400, 400) == .5f,
                "large timestamps preserve small elapsed intervals");

        float previous = 0;
        for (long time = 1000; time <= 1400; time++) {
            float eased = Motion.ease(Motion.progress(time, 1000, 400));
            check(Float.isFinite(eased) && eased >= previous && eased <= 1,
                    "animation advances monotonically");
            previous = eased;
        }

        check(Motion.lerp(10, 30, 0) == 10 && Motion.lerp(10, 30, 1) == 30,
                "interpolation reaches exact endpoints");
        check(Motion.lerp(10, 30, .5f) == 20, "interpolation midpoint");
        check(Motion.lerp(10, 30, -1) == 10 && Motion.lerp(10, 30, 2) == 30,
                "interpolation cannot overshoot");
        check(Motion.lerp(-Float.MAX_VALUE, Float.MAX_VALUE, .5f) == 0,
                "finite endpoints do not overflow during interpolation");

        float interrupted = Motion.lerp(0, 1, Motion.ease(Motion.progress(1150, 1000, 400)));
        check(Motion.lerp(interrupted, 0, Motion.ease(Motion.progress(1150, 1150, 250)))
                == interrupted, "reversing preserves the current position");
        float reversing = Motion.lerp(interrupted, 0, Motion.ease(Motion.progress(1250, 1150, 250)));
        check(reversing > 0 && reversing < interrupted, "reversal heads smoothly toward its target");
        check(Motion.lerp(interrupted, 0, Motion.ease(Motion.progress(1400, 1150, 250))) == 0,
                "reversal completes exactly");
        System.out.println("MotionTest: PASS " + checks + " assertions");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
