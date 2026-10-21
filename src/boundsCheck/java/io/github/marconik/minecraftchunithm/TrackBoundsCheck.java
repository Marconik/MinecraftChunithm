package io.github.marconik.minecraftchunithm;

/** Exhaustively checks 16 widths at every half-lane slot, including negative world coordinates. */
public final class TrackBoundsCheck {
    public static void main(String[] args) {
        int checked = 0;
        for (double min : new double[] {-100.5, 0.5, 200.5}) {
            for (boolean alongZ : new boolean[] {false, true}) {
                var bounds = new TrackBounds(min, min + 8, alongZ);
                for (int width = 1; width <= 16; width++) {
                    for (int slot = -1; slot <= 17; slot++) {
                        boolean expected = slot >= 0 && slot + width <= 16;
                        boolean actual = bounds.contains(min + slot * 0.5, width * 0.5);
                        if (expected != actual) throw new AssertionError("Boundary mismatch: " + bounds
                                + ", width=" + width + ", slot=" + slot);
                        checked++;
                    }
                }
            }
        }
        System.out.println("TAP boundary checks passed: " + checked);
        for (int beat=0;beat<10000;beat++) {
            if (RhythmTiming.beatTime(beat,120) != beat*0.5) throw new AssertionError("BPM drift");
        }
        if (RhythmTiming.position(0,1,10)-RhythmTiming.position(0,0,10) != 10)
            throw new AssertionError("Incorrect blocks/second");
        for (int length : new int[] {2,24,128}) {
            double atLine=(RhythmTiming.judgment(length)-0.25)/10;
            if (RhythmTiming.expired(0,atLine,10,length)
                    || RhythmTiming.expired(0,atLine+0.025,10,length))
                throw new AssertionError("Notes must survive passing the judgment line");
            double arrival=(length-0.25)/10;
            if (RhythmTiming.expired(0,arrival-0.0001,10,length)
                    || !RhythmTiming.expired(0,arrival+0.0001,10,length))
                throw new AssertionError("Incorrect disappearance time");
        }
        var rng=new java.util.Random(120);
        for (int i=0;i<10000;i++) {
            int width=rng.nextInt(16)+1, lane=rng.nextInt(17-width);
            if (!new TrackBounds(0.5,8.5,false).contains(0.5+lane*0.5,width*0.5))
                throw new AssertionError("Random TAP exceeded borders");
        }
        System.out.println("120 BPM / 10 blocks per second timing, despawn and random widths passed.");
    }
}
