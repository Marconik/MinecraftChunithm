package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.util.*;

/** The song panel's three explicit inputs. Null means an actually empty slot. */
public record SongQueueData(List<SongDisplayData> slots) {
    public SongQueueData {
        if(slots==null||slots.size()!=3)throw new IllegalArgumentException("Exactly three song inputs required");
        for(int i=0;i<3;i++)if(slots.get(i)!=null&&slots.get(i).track()!=i+1)
            throw new IllegalArgumentException("Song input TRACK must match its slot");
        slots=Collections.unmodifiableList(new ArrayList<>(slots));
    }
    public static SongQueueData empty(){return new SongQueueData(Arrays.asList(null,null,null));}
    public SongQueueData withSlot(int slot,SongDisplayData value){
        if(slot<0||slot>=3)throw new IllegalArgumentException("Invalid song input slot");
        var copy=new ArrayList<>(slots);copy.set(slot,value);return new SongQueueData(copy);
    }
    boolean sameSongs(SongQueueData other){
        for(int i=0;i<3;i++){
            var a=slots.get(i);var b=other.slots.get(i);
            if(a==null||b==null){if(a!=b)return false;continue;}
            if(!a.coverModel().equals(b.coverModel())||!a.title().equals(b.title())||!a.artist().equals(b.artist())
                    ||a.difficulty()!=b.difficulty()||!a.level().equals(b.level()))return false;
        }
        return true;
    }
    public JsonArray encode(){
        var array=new JsonArray();for(var song:slots)array.add(song==null?JsonNull.INSTANCE:PartConnections.encodeSong(song));return array;
    }
    public static SongQueueData decode(JsonArray array){
        List<SongDisplayData> slots=new ArrayList<>();for(var entry:array)slots.add(entry.isJsonNull()?null:PartConnections.decodeSong(entry.getAsJsonObject()));
        return new SongQueueData(slots);
    }
    /** Preserve completion only when the same playable chart remains in the same slot. */
    MusicQueueData resolve(MusicQueueData previous,java.util.function.Function<SongDisplayData,MusicQueueData.Choice> lookup,double speed,boolean mirror){
        List<MusicQueueData.Choice> choices=new ArrayList<>();
        for(int i=0;i<3;i++){
            var next=slots.get(i)==null?MusicQueueData.Choice.empty():lookup.apply(slots.get(i));var old=previous.slots().get(i);
            choices.add(new MusicQueueData.Choice(next.songId(),next.difficulty(),next.songId()>0&&old.songId()==next.songId()&&old.difficulty().equals(next.difficulty())&&old.played()));
        }
        return new MusicQueueData(speed,mirror,choices);
    }
}
