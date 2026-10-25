package io.github.marconik.minecraftchunithm;

import java.util.*;

final class DifferentialScrollCheck {
    static void run(ChartDefinition chart){
        var assigned=chart.notes().stream().filter(n->n.scrollProfile()==1).toList();
        if(assigned.size()!=8||chart.profiles().size()!=1||chart.speeds().size()!=5)
            throw new AssertionError("mu3 profile curves or 4 Tap/Air assignments were lost");
        for(double t:new double[]{123125,125000,126875,128750}){
            var pair=assigned.stream().filter(n->n.time()==t).toList();
            if(pair.size()!=2||!pair.stream().map(ChartDefinition.Note::type).collect(java.util.stream.Collectors.toSet()).equals(Set.of("TAP","AIR")))
                throw new AssertionError("Each selected Tap must keep its Air on the same curve");
            for(var n:pair)if(n.lane()!=6||n.width()!=4)throw new AssertionError("SLA changed musical note bounds");
        }
        double[] at={122800,124700,126500,128400},factors={.45,.4,.35,.3};
        int pairs=0;
        for(int i=0;i<at.length;i++){
            double a=at[i],b=a+50;
            double normal=chart.position(130000,b,20,24,false,0)-chart.position(130000,a,20,24,false,0);
            double special=chart.position(130000,b,20,24,false,1)-chart.position(130000,a,20,24,false,1);
            if(Math.abs(normal-.5)>1e-7||Math.abs(special-factors[i])>1e-7||special>=normal)
                throw new AssertionError("Differential speeds must coexist, not multiply or slow the whole track");
            for(boolean mirror:new boolean[]{false,true}){
                var before=ChartPlayback.checkFrame(chart,20,mirror,a);var after=ChartPlayback.checkFrame(chart,20,mirror,b);
                for(var entry:before.entrySet()){
                    var old=entry.getValue();var next=after.get(entry.getKey());
                    if(next==null||old.item().endsWith("_body")||old.item().equals("beat_line")||old.item().equals("speed_change_line"))continue;
                    if(old.z()>.25&&old.z()<23.75&&next.z()>.25&&next.z()<23.75){
                        double expected=old.profile()==1?factors[i]:.5;
                        if(Math.abs(next.z()-old.z()-expected)>1e-6)throw new AssertionError("Rendered cap ignored its profile");
                        pairs++;
                    }
                }
            }
        }
        if(pairs==0)throw new AssertionError("No actual profile note geometry was exercised");
        if(Math.abs(chart.scrollTime(50000,0)-chart.scrollTime(50000,1))>1e-8)
            throw new AssertionError("Alternate profile must inherit the default curve outside its own overrides");
        // Boundaries are continuous, and each note still reaches the stripe at its own timestamp.
        for(var event:chart.speeds(1))for(double t:new double[]{event.start(),event.end()})
            if(Math.abs(chart.scrollTime(t+.00001,1)-chart.scrollTime(t-.00001,1))>.02)
                throw new AssertionError("Profile boundary introduced a position discontinuity");
        for(var n:chart.notes())for(double t:new double[]{n.time(),n.end()})
            if(chart.position(t,t,20,24,false,n.scrollProfile())!=RhythmTiming.judgment(24))throw new AssertionError("Profile shifted a judgment time");
        // Synthetic moving, tapered sustain on an alternate profile: both endpoints and the
        // body must use the same curve, including an internal speed-change boundary.
        var note=new ChartDefinition.Note("path","SLD",1000,2500,0,2,12,4,"",true,true,5,5,"DEF",1);
        var sustain=new ChartDefinition("profile sustain",List.of(note),List.of(),List.of(),List.of(),2500,
                Map.of(1,List.of(new ChartDefinition.Speed(0,1500,.5),new ChartDefinition.Speed(1500,3000,.25))));
        for(boolean mirror:new boolean[]{false,true})for(double t:new double[]{500,1000,1499,1500,1501,2000})ChartPlayback.checkGeometry(sustain,10,mirror,t);
        System.out.println("mu3 differential scroll: 8 linked Tap/Air records, four alternate rates alongside default 0.5, "+pairs+" rendered cap pairs, mirrors, judgment times and tapered sustain continuity passed.");
    }
}
