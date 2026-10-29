package io.github.marconik.minecraftchunithm;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ImportedHeightCheck {
    static void run(){
        for(var entry:Map.of("2891",91.0,"2718",72.6).entrySet()){
            ChartDefinition chart;
            try(var stream=ImportedHeightCheck.class.getResourceAsStream("/data/minecraftchunithm/songs/"+entry.getKey()+"/master.json")){
                chart=ChartDefinition.fromJson(JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(IOException error){throw new AssertionError(error);}
            double max=chart.notes().stream().mapToDouble(n->Math.max(n.height(),n.endHeight())).max().orElseThrow();
            if(max!=entry.getValue())throw new AssertionError("Imported spatial heights were clamped");
            for(var note:chart.notes())if(Math.max(note.height(),note.endHeight())>32){
                for(double time:new double[]{note.time()-500,note.time(),(note.time()+note.end())/2})
                    for(boolean mirror:new boolean[]{false,true}){
                        var frame=ChartPlayback.checkFrame(chart,20,mirror,time);
                        ChartPlayback.checkGeometry(chart,20,mirror,time);
                        if(time==note.time()&&frame.values().stream().noneMatch(s->s.y()>6.4))
                            throw new AssertionError("High Air/decoration disappeared from the frame");
                    }
            }
        }
        for(double invalid:new double[]{-1,129,Double.NaN,Double.POSITIVE_INFINITY}){
            var note=new ChartDefinition.Note("bad","ALD",0,100,4,2,4,2,"",false,false,invalid,invalid,"LIM");
            try{new ChartDefinition("bad",List.of(note),List.of(),List.of(),List.of(),100);throw new AssertionError("Malformed height accepted");}
            catch(IllegalArgumentException expected){}
        }
        System.out.println("Imported spatial heights: Sou 91 and I Wanna 72.6 preserved, both mirror frames and finite height bounds passed.");
    }
}
