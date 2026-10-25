package io.github.marconik.minecraftchunithm;

/** All distances are blocks; time is game seconds (20 server ticks). */
public final class RhythmTiming {
    private RhythmTiming() {}
    public static final double SPAWN_CENTER = 0.25;
    // Publish the endpoint before removal, allowing the display's 1-tick lerp to finish.
    public static final int ENDPOINT_GRACE_TICKS = 2;
    public static double beatTime(long beat, double bpm) { return beat * 60.0 / bpm; }
    public static double position(double bornAt, double now, double speed) {
        return SPAWN_CENTER + Math.max(0, now - bornAt) * speed;
    }
    /** Note-centre arrival time targets this stripe, half a block before the end. */
    public static double judgment(int length) { return length - 0.5; }
    public static double end(int length) { return length; }
    public static double displayPosition(double bornAt, double now, double speed, int length) {
        return Math.min(position(bornAt, now, speed), end(length));
    }
    public static boolean canRemove(long reachedAtTick, long currentTick) {
        return reachedAtTick >= 0 && currentTick - reachedAtTick >= ENDPOINT_GRACE_TICKS;
    }
    public static boolean expired(double bornAt, double now, double speed, int length) {
        return position(bornAt, now, speed) >= end(length);
    }
}
