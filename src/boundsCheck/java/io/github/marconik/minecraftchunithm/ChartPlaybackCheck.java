package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ChartPlaybackCheck {
    private ChartPlaybackCheck(){}
    public static void run(){
        int frames=0,peak=0;
        Map<String,Integer> expectedRecords=Map.of("0103",2196,"0180",2401,"0697",1603,"0465",2299,
                "0258",3921,"2475",3789,"2354",2298,"2241",4682,"1100",1372);
        for(String id:expectedRecords.keySet()){
            ChartDefinition chart;
            try(var stream=ChartPlaybackCheck.class.getResourceAsStream("/data/minecraftchunithm/songs/"+id+"/master.json")){
                if(stream==null)throw new AssertionError("Missing chart resource");
                chart=ChartDefinition.fromJson(JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(IOException error){throw new AssertionError(error);}
            if(chart.notes().size()!=expectedRecords.get(id))throw new AssertionError("Source records were dropped: "+id);
            for(var note:chart.notes())for(double t:new double[]{note.time(),(note.time()+note.end())/2,note.end()}){
                double normal=note.laneAt(t,false),mirror=note.laneAt(t,true),width=note.widthAt(t);
                if(Math.abs(normal+mirror+width-16)>1e-8||mirror< -1e-8||mirror+width>16+1e-8)
                    throw new AssertionError("Mirrored path exceeds lanes: "+id+" "+note.id());
            }
            for(boolean mirror:new boolean[]{false,true})for(double speed:new double[]{3,10,20})
                for(double time:new double[]{-1000,0,1200,8000,28000,30000,45000,60000,71000,85000,
                        85200,85300,85500,86400,86500,86700,87600,87700,87900,100000,120000,140000,146400,166926,172000}){
                    peak=Math.max(peak,ChartPlayback.checkGeometry(chart,speed,mirror,time));frames++;
                }
            for(var effect:chart.speeds()){
                if(effect.multiplier()==0&&Math.abs(chart.scrollTime(effect.start())-chart.scrollTime(effect.end()))>1e-8)
                    throw new AssertionError("Zero-speed effect must freeze visual time");
                if(chart.position(effect.start(),effect.start(),10,24,false)!=RhythmTiming.judgment(24))
                    throw new AssertionError("Notes must meet their judgment time");
                double travel=chart.position(effect.end(),effect.end(),10,24,true)-chart.position(effect.end(),effect.start(),10,24,true);
                if(Math.abs(travel-(effect.end()-effect.start())/100)>1e-8)
                    throw new AssertionError("Recovery marker must keep moving during stop");
                double middle=(effect.start()+effect.end())/2;
                double delta=chart.scrollTime(middle)-chart.scrollTime(effect.start());
                if(Math.abs(delta-(middle-effect.start())*effect.multiplier())>1e-6)
                    throw new AssertionError("Visual multiplier was changed");
                for(double time:new double[]{effect.start()-.01,effect.start(),middle,effect.end(),effect.end()+.01})
                    for(boolean mirror:new boolean[]{false,true}){
                        peak=Math.max(peak,ChartPlayback.checkGeometry(chart,10,mirror,time));frames++;
                    }
            }
            if(id.equals("0180")&&(chart.notes().size()!=2401||chart.markers().size()!=6))
                throw new AssertionError("Chart records were dropped");
            if(id.equals("0103")&&(chart.notes().size()!=2196||!chart.markers().isEmpty()))
                throw new AssertionError("Unexpected ID103 conversion");
        }
        var reverseNote=new ChartDefinition.Note("reverse_path","HLD",0,10000,7,2,7,2,"",true,true,5,5,"DEF");
        var reverseChart=new ChartDefinition("Reverse regression",List.of(reverseNote),
                List.of(new ChartDefinition.Speed(4000,6000,-3)),List.of(),List.of(),10000);
        if(ChartPlayback.checkGeometry(reverseChart,10,false,3000)==0)
            throw new AssertionError("Reverse path middle was culled with its off-screen ends");
        var leftArrow=new ChartDefinition.Note("left","AUL",0,0,0,1,0,1,"TAP",false,false,5,5,"DEF");
        if(!leftArrow.airVariant(false).equals("up_left")||!leftArrow.airVariant(true).equals("up_right"))
            throw new AssertionError("Mirrored Air arrow direction was not reversed");
        System.out.println("Chart playback geometry: "+frames+" frames, both mirrors, 3/10/20 blocks/sec, peak "+peak+" displays; timing, clipping and speed stops passed.");
    }
}
