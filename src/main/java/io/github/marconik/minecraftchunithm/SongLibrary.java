package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;

/** Server data-pack catalog; only real, imported chart resources are selectable. */
public final class SongLibrary {
    public record Chart(int songId,String title,String artist,String texture,String coverModel,
                        String difficulty,String level,String constant,String resource){}
    public record Difficulty(String name,String level,String constant,boolean available){}
    public record Song(int songId,String title,String artist,String texture,List<Difficulty> difficulties){
        public Song {difficulties=withUltimaSlot(difficulties);}
    }
    private static final Map<ResourceManager,List<Chart>> CACHE=new WeakHashMap<>();
    private static final Map<ResourceManager,List<Song>> CATALOG=new WeakHashMap<>();
    private SongLibrary(){}
    private static List<Difficulty> withUltimaSlot(List<Difficulty> difficulties){
        var slots=new ArrayList<Difficulty>();Difficulty ultima=null;
        for(var difficulty:difficulties){if(difficulty.name().equals("ULTIMA"))ultima=difficulty;else slots.add(difficulty);}
        if(ultima==null||ultima.constant().isBlank()||new java.math.BigDecimal(ultima.constant()).signum()<=0)
            ultima=new Difficulty("ULTIMA","","",false);
        int at=slots.size();for(int i=0;i<slots.size();i++)if(slots.get(i).name().equals("WORLD'S END")){at=i;break;}
        slots.add(at,ultima);return List.copyOf(slots);
    }
    private static JsonObject read(ResourceManager manager,String resource)throws IOException{
        try(var reader=manager.getResourceOrThrow(Identifier.parse(resource)).openAsReader()){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    public static List<Chart> charts(ServerLevel level){
        var manager=level.getServer().getResourceManager();
        return CACHE.computeIfAbsent(manager,key->{
            List<Chart> result=new ArrayList<>();
            try{
                for(var entry:read(key,"minecraftchunithm:songs/index.json").getAsJsonArray("songs")){
                    var song=read(key,entry.getAsJsonObject().get("metadata").getAsString());
                    var cover=song.getAsJsonObject("cover");
                    for(var difficulty:song.getAsJsonObject("charts").entrySet()){
                        var chart=difficulty.getValue().getAsJsonObject();
                        if(!chart.get("imported").getAsBoolean()||chart.get("chartResource").isJsonNull())continue;
                        String resource=chart.get("chartResource").getAsString();
                        if(key.getResource(Identifier.parse(resource)).isEmpty())continue;
                        String constant=displayConstant(chart.get("internalLevel").getAsString());
                        result.add(new Chart(song.get("id").getAsInt(),song.get("title").getAsString(),song.get("artist").getAsString(),
                            cover.get("texture").getAsString(),cover.get("itemModel").getAsString(),difficulty.getKey(),
                            chart.get("level").getAsInt()+(chart.get("levelDecimal").getAsInt()>=50?"+":""),constant,resource));
                    }
                }
            }catch(IOException|RuntimeException error){MinecraftChunithm.LOGGER.error("Cannot load song library",error);}
            result.sort(Comparator.comparingInt(Chart::songId).thenComparing(Chart::difficulty));
            return List.copyOf(result);
        });
    }
    public static Chart find(ServerLevel level,MusicQueueData.Choice choice){return charts(level).stream()
            .filter(c->c.songId()==choice.songId()&&c.difficulty().equals(choice.difficulty())).findFirst().orElse(null);}
    /** Metadata includes unavailable difficulties so the selector can show them disabled. */
    public static List<Song> catalog(ServerLevel level){
        var manager=level.getServer().getResourceManager();
        return CATALOG.computeIfAbsent(manager,key->{
            List<Song> songs=new ArrayList<>();var imported=charts(level);
            try{
                for(var entry:read(key,"minecraftchunithm:songs/index.json").getAsJsonArray("songs")){
                    var metadata=read(key,entry.getAsJsonObject().get("metadata").getAsString());int songId=metadata.get("id").getAsInt();
                    List<Difficulty> difficulties=new ArrayList<>();
                    for(var difficulty:metadata.getAsJsonObject("charts").entrySet()){
                        var data=difficulty.getValue().getAsJsonObject();String name=difficulty.getKey();
                        String label=data.get("level").getAsInt()+(data.get("levelDecimal").getAsInt()>=50?"+":"");
                        boolean available=Set.of("EXPERT","MASTER","ULTIMA").contains(name)&&imported.stream().anyMatch(c->c.songId()==songId&&c.difficulty().equals(name));
                        difficulties.add(new Difficulty(name,label,displayConstant(data.get("internalLevel").getAsString()),available));
                    }
                    songs.add(new Song(songId,metadata.get("title").getAsString(),metadata.get("artist").getAsString(),metadata.getAsJsonObject("cover").get("texture").getAsString(),List.copyOf(difficulties)));
                }
            }catch(IOException|RuntimeException error){MinecraftChunithm.LOGGER.error("Cannot load song selector metadata",error);}
            songs.sort(Comparator.comparingInt(Song::songId));return List.copyOf(songs);
        });
    }
    static String displayConstant(String value){
        String label=new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();
        return label.contains(".")?label:label+".0";
    }
}
