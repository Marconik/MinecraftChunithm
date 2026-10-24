package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.util.*;

/** Persistent per-machine settings. Empty slots are explicit, never demo songs. */
public record MusicQueueData(double speed,boolean mirror,List<Choice> slots) {
    public static final double MIN_SPEED=.25,MAX_SPEED=20,STEP=.25;
    public record Choice(int songId,String difficulty,boolean played){
        public Choice {
            if(songId<0||difficulty==null||!Set.of("BASIC","ADVANCED","EXPERT","MASTER","ULTIMA","WORLD'S END").contains(difficulty))
                throw new IllegalArgumentException("Invalid queue choice");
        }
        public static Choice empty(){return new Choice(0,"MASTER",false);}
    }
    public MusicQueueData {
        if(!Double.isFinite(speed)||speed<MIN_SPEED||speed>MAX_SPEED||slots==null||slots.size()!=3||slots.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("A queue needs three slots and a speed from 0.25 to 20");
        slots=List.copyOf(slots);
    }
    public static MusicQueueData empty(){return new MusicQueueData(10,false,List.of(Choice.empty(),Choice.empty(),Choice.empty()));}
    public MusicQueueData changeSpeed(int direction){return new MusicQueueData(Math.clamp(speed+Integer.signum(direction)*STEP,MIN_SPEED,MAX_SPEED),mirror,slots);}
    public MusicQueueData withChoice(int slot,Choice choice){
        if(slot<0||slot>=3)throw new IllegalArgumentException("Invalid queue slot");
        var copy=new ArrayList<>(slots);copy.set(slot,choice);return new MusicQueueData(speed,mirror,copy);
    }
    public JsonObject encode(){
        var o=new JsonObject();o.addProperty("speed",speed);o.addProperty("mirror",mirror);var entries=new JsonArray();
        for(var slot:slots){var entry=new JsonObject();entry.addProperty("songId",slot.songId());entry.addProperty("difficulty",slot.difficulty());entry.addProperty("played",slot.played());entries.add(entry);}
        o.add("slots",entries);return o;
    }
    public static MusicQueueData decode(JsonObject o){
        List<Choice> slots=new ArrayList<>();for(var value:o.getAsJsonArray("slots")){
            var entry=value.getAsJsonObject();slots.add(new Choice(entry.get("songId").getAsInt(),entry.get("difficulty").getAsString(),entry.get("played").getAsBoolean()));
        }
        return new MusicQueueData(o.get("speed").getAsDouble(),o.get("mirror").getAsBoolean(),slots);
    }
}
