package io.github.marconik.minecraftchunithm;

import java.util.*;

/** Independent endpoint and surface checks, including signed/reversed scroll geometry. */
final class OffTrackRampCheck {
    private static double expected(double lane,double width,double z,int length){
        double outside=Math.max(0,Math.max(-lane,lane+width-16))*.5;
        return Math.min(1,outside)*Math.max(0,Math.min(1,z/(length-.5)));
    }
    private static void near(double actual,double expected,String reason){
        if(Math.abs(actual-expected)>1e-7)throw new AssertionError(reason+": "+actual+" != "+expected);
    }
    static void run(ChartDefinition imported){
        for(int length:new int[]{2,24,128})for(int width=1;width<=16;width++)for(int lane=-16;lane<=32-width;lane++)
            for(double ratio:new double[]{0,.25,.5,1,1.2}){
                double z=(length-.5)*ratio;double value=OffTrackRamp.height(lane,width,z,length);
                near(value,expected(lane,width,z,length),"Ramp amount");
                near(value,OffTrackRamp.height(16-lane-width,width,z,length),"Mirrored surface");
                if(value<0||value>1)throw new AssertionError("Ramp outside 0..1");
            }
        var ground=new ChartDefinition.Note("ground","TAP",1000,1000,-1,4,-1,4,"",false,false,5,5,"DEF");
        var arrow=new ChartDefinition.Note("arrow","AIR",1000,1000,-1,4,-1,4,"TAP",false,false,5,5,"DEF");
        var body=new ChartDefinition.Note("bend","SLD",0,1500,4,8,-16,1,"",true,true,5,5,"DEF");
        var air=new ChartDefinition.Note("bar","ASC",1000,1500,-1,1,-2,1,"TAP",true,true,5,5,"DEF");
        var fixture=new ChartDefinition("Raised outside deck",List.of(ground,arrow,body,air),List.of(new ChartDefinition.Speed(500,750,.5)),List.of(),List.of(),1500,Map.of(),true);
        for(boolean mirror:new boolean[]{false,true})for(double time:new double[]{-1000,0,500,750,1000,1250,1500}){
            var frame=ChartPlayback.checkFrame(fixture,10,mirror,time);
            var tap=frame.get("ground");var up=frame.get("arrow");var bar=frame.get("bar_action_start");
            if(tap!=null){
                near(tap.y(),.025+expected(-1,4,tap.z(),24),"Cap surface");
                near(tap.y(),up.y(),"Attached Air/Tap height");
                near(bar.y(),1+expected(-1,1,bar.z(),24),"Bar must use its logical footprint");
                if(time==1000){
                    near(tap.y(),.025+.5*23.375/23.5,"Clipped judgment cap height");
                    near(tap.z()+tap.sz()*.25,23.5,"Cap must clip at the judgment stripe");
                }
            }
            for(var entry:frame.entrySet())if(entry.getKey().startsWith("bend_body_")){
                var s=entry.getValue();double za=s.z()-s.sz()/2,zb=s.z()+s.sz()/2;
                // Locate the source times through this fixture's piecewise scroll curve.
                double ta=inverse(fixture,time,za),tb=inverse(fixture,time,zb);
                double ya=.045+expected(body.laneAt(ta,false),body.widthAt(ta),za,24);
                double yb=.045+expected(body.laneAt(tb,false),body.widthAt(tb),zb,24);
                near(s.y()-s.lift()/2,ya,"Ribbon lower endpoint");
                near(s.y()+s.lift()/2,yb,"Ribbon upper endpoint");
                double middle=.045+expected(body.laneAt((ta+tb)/2,false),body.widthAt((ta+tb)/2),(za+zb)/2,24);
                if(Math.abs(s.y()-middle)>OffTrackRamp.MAX_ERROR+1e-7)throw new AssertionError("Ramp approximation seam/curve error");
            }
        }
        var reverse=new ChartDefinition("Raised reverse deck",fixture.notes(),List.of(new ChartDefinition.Speed(500,750,-1)),List.of(),List.of(),1500,Map.of(),true);
        for(double time:new double[]{0,499,500,600,749,750,1000,1500}){
            var normal=ChartPlayback.checkFrame(reverse,10,false,time);var mirror=ChartPlayback.checkFrame(reverse,10,true,time);
            for(var entry:normal.entrySet()){
                var s=entry.getValue();var m=mirror.get(entry.getKey());
                near(s.y(),m.y(),"Reverse mirror height");near(s.lift(),m.lift(),"Reverse mirror slope");
                if(entry.getKey().startsWith("bend_body_")&&(s.y()<.045||s.y()>1.045))throw new AssertionError("Reverse ramp exceeds height limit");
            }
            ChartPlayback.checkGeometry(reverse,10,false,time);
        }
        int caps=0;
        for(boolean mirror:new boolean[]{false,true})for(double time:new double[]{98800,123600,142200,165200}){
            var frame=ChartPlayback.checkFrame(imported,20,mirror,time);
            for(var n:imported.notes())if(Set.of("TAP","CHR","MNE","FLK","AIR","AUL","AUR","ADW","ADL","ADR").contains(n.type())){
                var s=frame.get(n.id());if(s==null)continue;
                near(s.y(),.025+expected(n.lane(),n.width(),s.z(),24),"Imported note ramp");caps++;
            }
        }
        if(caps==0)throw new AssertionError("No real caps sampled");
        System.out.println("Outside ramp: 0..1-block footprint scaling, head/judgment endpoints, mirrors, attached Air, logical action-bar width, tapered/reversed paths and "+caps+" imported caps passed.");
    }
    private static double inverse(ChartDefinition chart,double now,double z){
        // This fixture is monotone; the separate reverse case checks symmetry
        // without trying to invert an intentionally non-monotone scroll curve.
        double target=chart.scrollTime(now)+(23.5-z)*100;
        double[] points={0,500,750,1500};
        for(int i=0;i<points.length-1;i++){
            double a=points[i],b=points[i+1],sa=chart.scrollTime(a),sb=chart.scrollTime(b);
            if(target>=Math.min(sa,sb)-1e-7&&target<=Math.max(sa,sb)+1e-7)
                return a+(b-a)*(target-sa)/(sb-sa);
        }
        throw new AssertionError("Cannot invert test coordinate");
    }
}
