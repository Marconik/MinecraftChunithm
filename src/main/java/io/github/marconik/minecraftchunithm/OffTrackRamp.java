package io.github.marconik.minecraftchunithm;

import java.util.Set;

/** Raise the outside play surface towards the judgment line; source data stays intact. */
final class OffTrackRamp {
    static final double MAX_HEIGHT=1;
    static final double MAX_ERROR=1.0/256;
    private OffTrackRamp(){}

    static double peak(double lane,double width){
        // One source cell is half a Minecraft block. Use the full logical
        // footprint, before the 95% sustain-body reduction or bar decoration.
        return Math.clamp(Math.max(-lane,lane+width-16)*.5,0,MAX_HEIGHT);
    }
    static double height(double lane,double width,double z,int length){
        return peak(lane,width)*Math.clamp(z/RhythmTiming.judgment(length),0,1);
    }
    static void boundaries(Set<Double> samples,ChartDefinition.Note note){
        // Split where either edge enters/leaves the deck or reaches the 1-block
        // ceiling. Within each piece, peak height is a linear function of time.
        crossing(samples,note,note.lane(),note.endLane(),0);
        crossing(samples,note,note.lane(),note.endLane(),-2);
        crossing(samples,note,note.lane()+note.width(),note.endLane()+note.endWidth(),16);
        crossing(samples,note,note.lane()+note.width(),note.endLane()+note.endWidth(),18);
    }
    private static void crossing(Set<Double> samples,ChartDefinition.Note note,double from,double to,double edge){
        if(from==to)return;double fraction=(edge-from)/(to-from);
        if(fraction>0&&fraction<1)samples.add(note.time()+(note.end()-note.time())*fraction);
    }
    static int steps(double a,double middle,double b){
        // The product of a linear overflow and a linear track distance is
        // quadratic. Its midpoint chord error falls by steps squared.
        return Math.max(1,(int)Math.ceil(Math.sqrt(Math.abs(middle-(a+b)/2)/MAX_ERROR)));
    }
}
