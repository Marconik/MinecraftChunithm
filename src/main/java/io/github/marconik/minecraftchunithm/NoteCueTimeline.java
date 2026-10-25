package io.github.marconik.minecraftchunithm;

import java.util.*;

/** Audible note arrivals in chart time, independent of visual speed, mirror and decoration. */
public final class NoteCueTimeline {
    private NoteCueTimeline(){}
    public static List<Double> of(ChartDefinition chart){
        var times=new TreeSet<Double>();
        for(var note:chart.notes())switch(note.type()){
            case "TAP","CHR","FLK","AIR","AUL","AUR","ADW","ADL","ADR"->times.add(note.time());
            case "HLD","HXD"->{times.add(note.time());times.add(note.end());}
            case "SLD","SLC","SXD","SXC"->{if(note.root())times.add(note.time());if(note.terminal())times.add(note.end());}
            // Actual AIR arrows/action bars, excluding invisible path control nodes.
            case "AHD"->{times.add(note.time());times.add(note.end());}
            case "ASC","ASD"->{if(note.root()||note.type().equals("ASD"))times.add(note.time());if(note.terminal())times.add(note.end());}
            case "ALD"->{} // Decoration, including NON, is never an audible judgment.
            default->throw new IllegalArgumentException("Unsupported note cue: "+note.type());
        }
        return List.copyOf(times);
    }
    /** Monotonic between explicit resets: clock corrections cannot replay a previous note. */
    public static final class Cursor {
        private final List<Double> times;private int next;
        public Cursor(List<Double> times){this.times=List.copyOf(times);}
        public void reset(double now){
            int lo=0,hi=times.size();
            while(lo<hi){int middle=(lo+hi)>>>1;if(times.get(middle)<=now)lo=middle+1;else hi=middle;}
            next=lo;
        }
        /** A late frame replaces all already-expired triggers with the latest one, never a burst. */
        public double poll(double now){
            double latest=Double.NaN;
            while(next<times.size()&&times.get(next)<=now)latest=times.get(next++);
            return latest;
        }
    }
}
