package io.github.marconik.minecraftchunithm;

import java.util.*;

/** Temporary all-critical scoring of visible note arrivals, without player input judging. */
public final class AutoScoreTimeline {
    private final List<Double> arrivals;
    private AutoScoreTimeline(List<Double> arrivals){
        arrivals.sort(Double::compare);this.arrivals=List.copyOf(arrivals);
    }
    public record Snapshot(long passed,long total,long score){
        public HeadDisplayData display(){return new HeadDisplayData(passed,0,0,0,passed,score);}
    }
    public static AutoScoreTimeline of(ChartDefinition chart){
        var times=new ArrayList<Double>();
        for(var note:chart.notes())switch(note.type()){
            // Preserve duplicate timestamps: chords are multiple scoring notes.
            case "TAP","CHR","MNE","FLK","AIR","AUL","AUR","ADW","ADL","ADR"->times.add(note.time());
            case "HLD","HXD"->{times.add(note.time());times.add(note.end());}
            case "SLD","SLC","SXD","SXC"->{
                if(note.root()||note.type().endsWith("D"))times.add(note.time());
                if(note.terminal())times.add(note.end());
            }
            // Count a shared AHD boundary once, at the next segment's start.
            case "AHD","AHX"->{times.add(note.time());if(note.terminal())times.add(note.end());}
            case "ASC","ASD"->{
                if(note.root()||note.type().equals("ASD"))times.add(note.time());
                if(note.terminal())times.add(note.end());
            }
            case "ALD"->{} // Decoration has no score judgment.
            default->throw new IllegalArgumentException("Unsupported automatic score note: "+note.type());
        }
        return new AutoScoreTimeline(times);
    }
    /** Original musical time is authoritative, regardless of scroll direction or mirror. */
    public Snapshot at(double time){
        if(!Double.isFinite(time))throw new IllegalArgumentException("Invalid score time");
        int lo=0,hi=arrivals.size();
        while(lo<hi){int mid=(lo+hi)>>>1;if(arrivals.get(mid)<=time)lo=mid+1;else hi=mid;}
        long passed=lo,total=arrivals.size();
        // Integer arithmetic floors the cumulative ratio once; rounding each
        // individual note would lose points and fail to reach the exact maximum.
        return new Snapshot(passed,total,total==0?0:passed*HeadDisplayData.MAX_SCORE/total);
    }
}
