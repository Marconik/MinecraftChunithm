package io.github.marconik.minecraftchunithm;

/** All distances are blocks; time is game seconds (20 server ticks). */
public final class RhythmTiming {
    private RhythmTiming() {}
    public static final double SPAWN_CENTER = 0.25;
    public static double beatTime(long beat, double bpm) { return beat * 60.0 / bpm; }
    public static double position(double bornAt, double now, double speed) {
        return SPAWN_CENTER + Math.max(0, now - bornAt) * speed;
    }
    public static double judgment(int length) { return length - 0.5; }
    public static double end(int length) { return length; }
    public static boolean expired(double bornAt, double now, double speed, int length) {
        return position(bornAt, now, speed) >= end(length);
    }
}
