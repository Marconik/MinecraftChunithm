package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/** Immutable visual chart. Source segments remain independent across intersections. */
public record ChartDefinition(String title,List<Note> notes,List<Speed> speeds,List<Double> markers,List<Double> beats,double lastTimeMs) {
    public record Note(String id,String type,double time,double end,double lane,double width,
                       double endLane,double endWidth,String target,boolean root,boolean terminal,
                       double height,double endHeight,String color) {
        public double laneAt(double t,boolean mirror){
            double u=end>time?Math.clamp((t-time)/(end-time),0,1):0;
            double l=lane+(endLane-lane)*u,w=width+(endWidth-width)*u;
            return mirror?16-l-w:l;
        }
        public double widthAt(double t){return end>time?width+(endWidth-width)*Math.clamp((t-time)/(end-time),0,1):width;}
        public double heightAt(double t){return end>time?height+(endHeight-height)*Math.clamp((t-time)/(end-time),0,1):height;}
        public String airVariant(boolean mirror){return switch(type){
            case "AUL"->mirror?"up_right":"up_left";case "AUR"->mirror?"up_left":"up_right";
            case "ADL"->mirror?"down_right":"down_left";case "ADR"->mirror?"down_left":"down_right";
            case "ADW"->"down";default->"up";
        };}
    }
    public record Speed(double start,double end,double multiplier){}
    public ChartDefinition {
        notes=List.copyOf(notes);speeds=List.copyOf(speeds);markers=List.copyOf(markers);beats=List.copyOf(beats);
        if(notes.isEmpty()||notes.size()>100000||!Double.isFinite(lastTimeMs)||lastTimeMs<0)
            throw new IllegalArgumentException("Invalid chart size/time");
        Set<String> ids=new HashSet<>();
        for(var n:notes){
            if(!ids.add(n.id)||!Set.of("TAP","CHR","FLK","HLD","HXD","SLD","SLC","SXD","SXC",
                    "AIR","AUL","AUR","ADW","ADL","ADR","AHD","ASC","ASD","ALD").contains(n.type)
                    ||!Double.isFinite(n.time)||!Double.isFinite(n.end)||n.time<0||n.end<n.time||n.end>lastTimeMs+1e-6
                    ||!validLane(n.lane,n.width)||!validLane(n.endLane,n.endWidth)
                    ||!Double.isFinite(n.height)||!Double.isFinite(n.endHeight)||n.height<0||n.endHeight<0||n.height>32||n.endHeight>32)
                throw new IllegalArgumentException("Invalid chart note: "+n.id);
            if(Set.of("HLD","HXD","SLD","SLC","SXD","SXC","AHD","ASC","ASD","ALD").contains(n.type)!=(n.end>n.time))
                throw new IllegalArgumentException("Invalid sustain duration: "+n.id);
            if(n.type.equals("ALD")&&!Set.of("GRY","AQA","RED","BLK","DEF","DGR","GRN","PNK","CYN","BLU","ORN","YEL","PPL","NON").contains(n.color))
                throw new IllegalArgumentException("Unsupported effect color: "+n.color);
        }
        double previousEnd=-1;
        for(var s:speeds){
            if(!Double.isFinite(s.start)||!Double.isFinite(s.end)||!Double.isFinite(s.multiplier)
                    ||s.start<0||s.end<=s.start||s.start<previousEnd)
                throw new IllegalArgumentException("Invalid/overlapping speed effects");
            previousEnd=s.end;
        }
        for(double marker:markers)if(!Double.isFinite(marker)||marker<0)throw new IllegalArgumentException("Invalid marker time");
        for(double beat:beats)if(!Double.isFinite(beat)||beat<0)throw new IllegalArgumentException("Invalid beat time");
    }
    private static boolean validLane(double lane,double width){return Double.isFinite(lane)&&Double.isFinite(width)
            &&lane>=0&&width>=1&&lane+width<=16&&lane==Math.rint(lane)&&width==Math.rint(width);}
    /** Visual coordinate in milliseconds. Sound/judgment time is never stopped. */
    public double scrollTime(double time){
        double value=time;
        for(var s:speeds){if(time<=s.start)break;value+=(Math.min(time,s.end)-s.start)*(s.multiplier-1);}
        return value;
    }
    public double position(double noteTime,double now,double speed,int length,boolean marker){
        return RhythmTiming.judgment(length)+speed*(marker?now-noteTime:scrollTime(now)-scrollTime(noteTime))/1000;
    }
    public static ChartDefinition load(ServerLevel level,String resource) throws IOException {
        var id=resource.matches("\\d+")?Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,
                "songs/"+String.format(Locale.ROOT,"%04d",Integer.parseInt(resource))+"/master.json"):Identifier.parse(resource);
        JsonObject json;
        try(var reader=level.getServer().getResourceManager().getResourceOrThrow(id).openAsReader()){
            json=JsonParser.parseReader(reader).getAsJsonObject();
        }
        return fromJson(json);
    }
    public static ChartDefinition fromJson(JsonObject json){
        if(json.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported chart schema");
        if(json.has("visualWarnings"))for(var warning:json.getAsJsonArray("visualWarnings"))
            MinecraftChunithm.LOGGER.warn("Chart {} {}: {}",json.get("title"),json.get("difficulty"),warning.getAsString());
        List<Note> notes=new ArrayList<>();
        for(var value:json.getAsJsonArray("notes")){
            var n=value.getAsJsonObject();double t=n.get("timeMs").getAsDouble(),l=n.get("lane").getAsDouble(),w=n.get("width").getAsDouble();
            notes.add(new Note(n.get("id").getAsString(),n.get("command").getAsString(),t,
                    n.has("endTimeMs")?n.get("endTimeMs").getAsDouble():t,l,w,
                    n.has("endLane")?n.get("endLane").getAsDouble():l,n.has("endWidth")?n.get("endWidth").getAsDouble():w,
                    n.has("targetType")?n.get("targetType").getAsString():"",n.has("root")&&n.get("root").getAsBoolean(),
                    n.has("terminal")&&n.get("terminal").getAsBoolean(),
                    n.has("height")?n.get("height").getAsDouble():5,
                    n.has("endHeight")?n.get("endHeight").getAsDouble():5,
                    n.has("color")?n.get("color").getAsString():"DEF"));
        }
        List<Speed> speeds=new ArrayList<>();
        for(var value:json.getAsJsonArray("scrollEvents")){var s=value.getAsJsonObject();speeds.add(new Speed(
                s.get("timeMs").getAsDouble(),s.get("endTimeMs").getAsDouble(),s.get("multiplier").getAsDouble()));}
        speeds.sort(Comparator.comparingDouble(Speed::start));
        List<Double> markers=new ArrayList<>();for(var v:json.getAsJsonArray("speedChangeMarkers"))markers.add(v.getAsJsonObject().get("timeMs").getAsDouble());
        List<Double> beats=new ArrayList<>();if(json.has("beatTimesMs"))for(var v:json.getAsJsonArray("beatTimesMs"))beats.add(v.getAsDouble());
        return new ChartDefinition(json.get("title").getAsString(),notes,speeds,markers,beats,json.get("lastTimeMs").getAsDouble());
    }
}
