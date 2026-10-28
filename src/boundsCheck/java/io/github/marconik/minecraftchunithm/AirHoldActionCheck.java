package io.github.marconik.minecraftchunithm;

import java.util.List;

final class AirHoldActionCheck {
    static void run(){
        var note=new ChartDefinition.Note("action","AHX",1000,1500,4,2,4,2,"TAP",true,true,0,0,"DEF");
        var chart=new ChartDefinition("AHX action",List.of(note),List.of(),List.of(),List.of(),1500);
        for(boolean mirror:new boolean[]{false,true}){
            var frame=ChartPlayback.checkFrame(chart,3,mirror,1000);
            if(frame.values().stream().anyMatch(s->s.item().startsWith("chart_air_arrow")))throw new AssertionError("AHX must not add a green direction arrow");
            if(frame.values().stream().filter(s->s.item().startsWith("chart_action_bar")).count()!=2
                    ||frame.values().stream().noneMatch(s->s.item().equals("chart_air_body")))throw new AssertionError("AHX action endpoints/beam missing");
            if(frame.values().stream().anyMatch(s->s.y()!=NoteDefinition.AIR_ACTION_HEIGHT||s.lift()!=0))
                throw new AssertionError("Ordinary Air Hold/Action must retain the one-block height");
        }
        if(!NoteCueTimeline.of(chart).equals(List.of(1000.0,1500.0)))throw new AssertionError("AHX cue times missing");
        var score=AutoScoreTimeline.of(chart);
        if(score.at(999).passed()!=0||score.at(1000).passed()!=1||score.at(1500).passed()!=2||score.at(1500).score()!=1010000)
            throw new AssertionError("AHX score endpoints missing");
        System.out.println("AHX: preserved action beam/endpoints, no extra Air arrow, both mirrors, sound cues and automatic score passed.");
    }
}
