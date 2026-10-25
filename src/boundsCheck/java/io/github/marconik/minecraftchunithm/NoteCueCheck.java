package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.minecraft.core.*;

final class NoteCueCheck {
    private static ChartDefinition.Note note(String type,double start,double end,boolean root,boolean terminal){
        return new ChartDefinition.Note(type+start,type,start,end,0,2,0,2,"",root,terminal,5,5,type.equals("ALD")?"RED":"DEF");
    }
    static void run(){
        var notes=new ArrayList<ChartDefinition.Note>();
        notes.add(note("TAP",0,0,false,false));notes.add(note("CHR",0,0,false,false));notes.add(note("FLK",50,50,false,false));
        String[] air={"AIR","AUL","AUR","ADW","ADL","ADR"};
        for(int i=0;i<air.length;i++)notes.add(note(air[i],100+i,100+i,false,false));
        notes.add(note("HLD",200,400,true,true));notes.add(note("HXD",250,450,true,true));
        notes.add(note("SLD",500,600,true,false));notes.add(note("SLC",600,700,false,false));notes.add(note("SLD",700,800,false,true));
        notes.add(note("SXD",900,950,true,false));notes.add(note("SXC",950,1000,false,true));
        notes.add(note("AHD",1100,1200,true,true));
        notes.add(note("ASC",1300,1400,true,false));notes.add(note("ASC",1400,1500,false,false));notes.add(note("ASD",1500,1600,false,true));
        notes.add(note("MNE",1650,1650,false,false));notes.add(note("MNE",0,0,false,false));
        notes.add(note("ALD",1700,1800,true,true));
        notes.add(new ChartDefinition.Note("non","ALD",1900,2000,0,2,0,2,"",true,true,5,5,"NON"));
        var chart=new ChartDefinition("Cue boundaries",notes,List.of(new ChartDefinition.Speed(30,40,0)),List.of(20.0),List.of(10.0),2000);
        var expected=List.of(0.0,50.0,100.0,101.0,102.0,103.0,104.0,105.0,200.0,250.0,400.0,450.0,500.0,800.0,900.0,1000.0,1100.0,1200.0,1300.0,1500.0,1600.0,1650.0);
        var times=NoteCueTimeline.of(chart);
        if(!times.equals(expected))throw new AssertionError("Cue events must include only real note heads/tails and merge simultaneous hits: "+times);
        var cursor=new NoteCueTimeline.Cursor(times);cursor.reset(-100);
        if(!Double.isNaN(cursor.poll(-1))||cursor.poll(0)!=0||!Double.isNaN(cursor.poll(0)))throw new AssertionError("First note was lost or repeated");
        if(cursor.poll(105)!=105||!Double.isNaN(cursor.poll(50))||cursor.poll(200)!=200)throw new AssertionError("Dense notes must replace rather than queue; backward clock correction must not replay");
        cursor.reset(700);
        if(!Double.isNaN(cursor.poll(799))||cursor.poll(800)!=800)throw new AssertionError("Seek replayed old notes or skipped slide tail");
        cursor.reset(0);
        if(!Double.isNaN(cursor.poll(0))||cursor.poll(50)!=50)throw new AssertionError("Resume must not repeat the note at the paused time");
        cursor.reset(-100);
        if(cursor.poll(0)!=0)throw new AssertionError("Restart must rearm the first note");
        for(double time:times)for(double speed:new double[]{.25,20,30})
            if(chart.position(time,time,speed,24,false)!=RhythmTiming.judgment(24))throw new AssertionError("Cue is not aligned with the judgment stripe");
        for(var facing:Direction.Plane.HORIZONTAL){
            var track=new Placement(new BlockPos(-23,64,47),facing);
            var ground=PartConnections.groundTarget(track,24);
            var left=NoteCueAudio.position(AirSensors.target(ground,true),true);
            var right=NoteCueAudio.position(AirSensors.target(ground,false),false);
            var delta=right.subtract(left);var playerRight=facing.getOpposite().getClockWise();
            if(delta.x*playerRight.getStepX()+delta.z*playerRight.getStepZ()<=0||left.y!=right.y||left.y!=67)
                throw new AssertionError("Cue emitters must occupy the matching player-side middle AIR apertures");
        }
        System.out.println("Note cues: all Tap/EX/Hold/Slide/Air/Flick endpoints, simultaneous-hit merge, dense replacement, seek/resume/restart, judgment timing and four-facing sensor positions passed.");
    }
}
