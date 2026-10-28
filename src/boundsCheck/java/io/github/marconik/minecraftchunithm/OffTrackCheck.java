package io.github.marconik.minecraftchunithm;

import java.util.*;

/** Original signed coordinates must survive loading, mirroring and entity geometry. */
final class OffTrackCheck {
    static void run(ChartDefinition chart){
        if(!chart.extendedLanes()||chart.notes().size()!=6166)throw new AssertionError("Melodiniq extended chart not loaded");
        long outside=chart.notes().stream().filter(n->n.lane()<0||n.lane()+n.width()>16||n.endLane()<0||n.endLane()+n.endWidth()>16).count();
        if(outside!=2559)throw new AssertionError("Source excursions were lost: "+outside);
        var spike=chart.notes().stream().filter(n->n.id().equals("c2s_line_2160")).findFirst().orElseThrow();
        if(spike.lane()!=4||spike.width()!=8||spike.endLane()!=-16||spike.endWidth()!=1)
            throw new AssertionError("Source one-tick spike/width ratio changed");
        int caps=0,bodies=0,raised=0;
        for(boolean mirror:new boolean[]{false,true})for(double t:new double[]{97000,98800,101800,123600,137800,142200,165200}){
            var frame=ChartPlayback.checkFrame(chart,20,mirror,t);
            for(var n:chart.notes())if(Set.of("TAP","CHR","MNE","FLK","AIR","AUL","AUR","ADW","ADL","ADR").contains(n.type())){
                var s=frame.get(n.id());if(s==null)continue;
                double left=n.laneAt(n.time(),mirror),center=.5+(left+n.width()/2)*.5;
                if(Math.abs(s.x()-center)>1e-8||s.sx()!=1)throw new AssertionError("Cap was clamped or resized: "+n.id());
                if(left<0||left+n.width()>16)caps++;
            }
            for(var s:frame.values())if(s.item().endsWith("_body")){
                if(s.x()-s.sx()/2-Math.abs(s.shear())/2<.5||s.x()+s.sx()/2+Math.abs(s.shear())/2>8.5)bodies++;
                if(s.item().equals("chart_air_body")&&(s.y()!=1||s.lift()!=0))raised++;
            }
        }
        if(caps==0||bodies==0||raised==0)throw new AssertionError("Test did not observe off-track notes, ribbons and source heights");
        var n=new ChartDefinition.Note("taper","SLD",0,1000,-8,16,24,4,"",true,true,5,5,"DEF");
        var air=new ChartDefinition.Note("air","ASC",0,1000,-8,16,24,4,"CHR",true,true,1,20,"DEF");
        var fixture=new ChartDefinition("extended tapered paths",List.of(n,air),List.of(),List.of(),List.of(),1000,Map.of(),true);
        for(boolean mirror:new boolean[]{false,true}){
            var frame=ChartPlayback.checkFrame(fixture,10,mirror,0);
            double center=.5+(mirror?16-(-8)-8:-8+8)*.5;
            var head=frame.get("taper_node");var tail=frame.get("taper_tail");
            if(head.sx()!=1||tail.sx()!=1||Math.abs(head.x()-center)>1e-8)throw new AssertionError("Full-width endpoints changed");
            for(var s:frame.values())if(s.item().equals("chart_slide_body")){
                double a=s.z()-s.sz()/2,b=s.z()+s.sz()/2;
                double ta=(RhythmTiming.judgment(24)-a)*100,tb=(RhythmTiming.judgment(24)-b)*100;
                double expected=Math.min(n.widthAt(ta),n.widthAt(tb))*.5*.95;
                if(Math.abs(s.sx()-expected)>1e-7)throw new AssertionError("95% taper changed outside deck");
            }
            if(Math.abs(frame.get("air_action_start").y()-(.2+frame.get("air_action_start").z()/23.5))>1e-8
                    ||Math.abs(frame.get("air_action_end").y()-(4+13.5/23.5))>1e-8)
                throw new AssertionError("Air Slide bars did not follow source heights");
            ChartPlayback.checkGeometry(fixture,10,mirror,0);
        }
        try{new ChartDefinition("invalid bounded chart",List.of(n),List.of(),List.of(),List.of(),1000);throw new AssertionError("Unflagged outside chart accepted");}
        catch(IllegalArgumentException expected){}
        var boundedNotes=chart.notes().stream().map(p->new ChartDefinition.Note(p.id(),p.type(),p.time(),p.end(),
                Math.clamp(p.lane(),0,16-p.width()),p.width(),Math.clamp(p.endLane(),0,16-p.endWidth()),p.endWidth(),
                p.target(),p.root(),p.terminal(),p.height(),p.endHeight(),p.color(),p.scrollProfile())).toList();
        var bounded=new ChartDefinition(chart.title(),boundedNotes,chart.speeds(),chart.markers(),chart.beats(),chart.lastTimeMs(),chart.profiles());
        if(!NoteCueTimeline.of(bounded).equals(NoteCueTimeline.of(chart)))throw new AssertionError("Off-track motion changed sound timing");
        var score=AutoScoreTimeline.of(chart);var old=AutoScoreTimeline.of(bounded);
        for(double t=0;t<chart.lastTimeMs()+250;t+=250)if(!score.at(t).equals(old.at(t)))throw new AssertionError("Off-track motion changed score/progress");
        if(score.at(chart.lastTimeMs()).score()!=1010000)throw new AssertionError("Off-track score cannot finish");
        if(GroundSlidePlayback.lanes(-8,4)!=0||GroundSlidePlayback.lanes(20,4)!=0
                ||GroundSlidePlayback.lanes(-2,4)!=3||GroundSlidePlayback.lanes(14,4)!=0xc000)
            throw new AssertionError("Outside notes lit a clamped Ground Slide cell");
        OffTrackRampCheck.run(chart);
        System.out.println("Melodiniq off-track: 2559 original excursions, "+caps+" caps, "+bodies+" ribbons, "+raised+" spatial Air segments; mirrors, tapered widths, heights, unchanged cues/score and Ground Slide intersections passed.");
    }
}
