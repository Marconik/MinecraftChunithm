package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ChartPlaybackCheck {
    private ChartPlaybackCheck(){}
    public static void run(){
        checkSustainWidths();
        int frames=0,peak=0;
        Map<String,Integer> expectedRecords=new HashMap<>(Map.of("0103",2196,"0180",2401,"0697",1603,"0465",2299,
                "0258",3921,"2475",3789,"2354",2298,"2241",4682,"1100",1372));
        expectedRecords.put("2967",2975);expectedRecords.put("2967/ultima",6460);
        expectedRecords.put("2712",2548);expectedRecords.put("2712/ultima",3754);
        expectedRecords.putAll(Map.of("2838",4047,"0464",3328,"2279",1872,"1035",3649));
        for(String id:expectedRecords.keySet()){
            ChartDefinition chart;
            try(var stream=ChartPlaybackCheck.class.getResourceAsStream("/data/minecraftchunithm/songs/"+id+(id.contains("/")?"":"/master")+".json")){
                if(stream==null)throw new AssertionError("Missing chart resource");
                chart=ChartDefinition.fromJson(JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(IOException error){throw new AssertionError(error);}
            if(chart.notes().size()!=expectedRecords.get(id))throw new AssertionError("Source records were dropped: "+id);
            if(id.startsWith("2967")||id.startsWith("2712")||Set.of("2838","0464","2279","1035").contains(id))for(double time=0;time<=chart.lastTimeMs();time+=250)
                for(boolean mirror:new boolean[]{false,true})for(double speed:new double[]{3,10,20,30}){
                    peak=Math.max(peak,ChartPlayback.checkGeometry(chart,speed,mirror,time));frames++;
                }
            for(var note:chart.notes())for(double t:new double[]{note.time(),(note.time()+note.end())/2,note.end()}){
                double normal=note.laneAt(t,false),mirror=note.laneAt(t,true),width=note.widthAt(t);
                if(Math.abs(normal+mirror+width-16)>1e-8||mirror< -1e-8||mirror+width>16+1e-8)
                    throw new AssertionError("Mirrored path exceeds lanes: "+id+" "+note.id());
            }
            // Every note endpoint reaches the same stripe that TrackMotion.addJudgment draws.
            for(var note:chart.notes())for(double t:new double[]{note.time(),note.end()})
                for(int length:new int[]{2,24,128})for(double speed:new double[]{.25,20,30}){
                    double at=chart.position(t,t,speed,length,false);
                    if(at!=RhythmTiming.judgment(length)||at==RhythmTiming.end(length))
                        throw new AssertionError("Arrival must target the judgment stripe, not the track end");
                }
            for(boolean mirror:new boolean[]{false,true})for(double speed:new double[]{3,10,20,30})
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
        System.out.println("Chart playback geometry: "+frames+" frames, both mirrors, 3/10/20/30 blocks/sec, peak "+peak+" displays; judgment-line arrival, clipping and speed stops passed.");
    }
    private static void checkSustainWidths(){
        for(int width=1;width<=16;width++)for(boolean mirror:new boolean[]{false,true}){
            var notes=new ArrayList<ChartDefinition.Note>();
            for(String type:List.of("HLD","HXD","SLD","SXD")){
                notes.add(new ChartDefinition.Note(type+"_root",type,0,100,16-width,width,16-width,width,"",true,type.startsWith("H"),5,5,"DEF"));
                if(type.startsWith("S")){
                    notes.add(new ChartDefinition.Note(type+"_middle",type,100,200,16-width,width,16-width,width,"",false,false,5,5,"DEF"));
                    notes.add(new ChartDefinition.Note(type+"_last",type.equals("SLD")?"SLC":"SXC",200,300,16-width,width,16-width,width,"",false,true,5,5,"DEF"));
                }
            }
            var chart=new ChartDefinition("Sustain width regression",notes,List.of(),List.of(),List.of(),300);
            var frame=ChartPlayback.checkFrame(chart,20,mirror,0);
            double center=.5+(mirror?width/2.0:16-width/2.0)*.5;
            for(var entry:frame.entrySet()){
                var spec=entry.getValue();boolean body=spec.item().endsWith("_body"),middle=entry.getKey().endsWith("_middle_node");
                double expected=body?width*.5*.95:middle?.95:1;
                if(Math.abs(spec.sx()-expected)>1e-8||Math.abs(spec.x()-center)>1e-8)
                    throw new AssertionError("Sustain must shrink symmetrically to 95%, retaining full-width endpoints: "+entry);
            }
            for(String type:List.of("SLD","SXD"))
                if(!frame.containsKey(type+"_root_node")||!frame.containsKey(type+"_middle_node")||!frame.containsKey(type+"_last_tail"))
                    throw new AssertionError("Slide head, intermediate node or tail was lost");
        }
        System.out.println("Hold/Slide widths: 16 widths in both mirrors, 95% centred bodies/intermediate nodes, full-width heads/tails passed.");
    }
}
