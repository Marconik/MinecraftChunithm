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
        for(var connection:NoteDefinition.Connection.values()) {
            var path=new NoteDefinition("curve",NoteDefinition.Kind.SLIDE,0,0,2,1000,
                    NoteDefinition.Direction.NONE,"",java.util.List.of(
                    new NoteDefinition.Node(0,0,2,0,connection),new NoteDefinition.Node(1000,14,2,1)));
            if(path.sample(0).lane()!=0 || path.sample(1000).lane()!=14)throw new AssertionError("Curve endpoint mismatch");
            double expected=connection==NoteDefinition.Connection.LINEAR?7:
                    connection==NoteDefinition.Connection.PARABOLA_UP?10.5:3.5;
            if(path.sample(500).lane()!=expected)throw new AssertionError("Wrong quadratic interpolation");
            for(int time=0;time<=1000;time++) {
                var node=path.sample(time);
                if(node.lane()<0 || node.lane()+node.width()>16)throw new AssertionError("Curve exceeds track");
            }
        }
        for (var kind:NoteDefinition.Kind.values()) for (int width=1;width<=16;width++) {
            if(kind==NoteDefinition.Kind.AIR_ACTION && width==1)continue;
            var definition=NoteDefinition.preview(kind,width);
            if (definition.width()!=width || definition.kind()!=kind) throw new AssertionError("Invalid note attributes");
        }
        try {
            new NoteDefinition.Node(0,15,2,0);
            throw new AssertionError("Out-of-bounds path accepted");
        } catch (IllegalArgumentException expected) { }
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
        // Simulate the actual discrete update/removal sequence, not only ideal arrival times.
        for (double speed : new double[] {3,10,20}) for (int length : new int[] {2,24,128}) {
            long arrived = -1;
            int endpointUpdates = 0;
            boolean removed = false;
            for (long tick = 0; tick < 2000; tick++) {
                if (RhythmTiming.canRemove(arrived,tick)) {
                    if (endpointUpdates < 2) throw new AssertionError("Removed before endpoint was displayed");
                    removed = true;
                    break;
                }
                double position = RhythmTiming.displayPosition(0,tick/20.0,speed,length);
                if (position > length) throw new AssertionError("Endpoint overshoot");
                if (position == length) endpointUpdates++;
                if (arrived < 0 && RhythmTiming.expired(0,tick/20.0,speed,length)) arrived=tick;
            }
            if (!removed) throw new AssertionError("Endpoint entity leaked");
        }
        System.out.println("Discrete endpoint delivery and delayed removal checks passed at 3, 10 and 20 blocks/second.");
        for (int i=0;i<10000;i++) {
            int width=rng.nextInt(16)+1, lane=rng.nextInt(17-width);
            if (!new TrackBounds(0.5,8.5,false).contains(0.5+lane*0.5,width*0.5))
                throw new AssertionError("Random TAP exceeded borders");
        }
        System.out.println("120 BPM / 10 blocks per second timing, despawn and random widths passed.");
    }
}
