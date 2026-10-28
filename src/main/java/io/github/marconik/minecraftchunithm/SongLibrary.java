package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;

/** Server data-pack catalog; only real, imported chart resources are selectable. */
public final class SongLibrary {
    public record Audio(String event,String resource,double duration,double offset){}
    public static Audio audio(ServerLevel level,int songId){
        try{
            var manager=level.getServer().getResourceManager();
            for(var entry:read(manager,"minecraftchunithm:songs/index.json").getAsJsonArray("songs")){
                var index=entry.getAsJsonObject();if(index.get("id").getAsInt()!=songId)continue;
                var audio=read(manager,index.get("metadata").getAsString()).getAsJsonObject("audio");
                if(audio==null)return null;
                var result=new Audio(audio.get("soundEvent").getAsString(),audio.get("resource").getAsString(),
                        audio.get("durationMs").getAsDouble(),audio.has("offsetMs")?audio.get("offsetMs").getAsDouble():0);
                Identifier.parse(result.event());Identifier.parse(result.resource());
                if(!Double.isFinite(result.duration())||result.duration()<=0||!Double.isFinite(result.offset()))return null;
                return result;
            }
        }catch(IOException|RuntimeException error){MinecraftChunithm.LOGGER.warn("Cannot load song audio metadata for {}",songId,error);}
        return null;
    }
    public record Chart(int songId,String title,String artist,String texture,String coverModel,
                        String difficulty,String level,String constant,String resource){}
    public record Difficulty(String name,String level,String constant,boolean available){}
    public record Genre(int id,String name){}
    public record Song(int songId,String title,String artist,String texture,List<Difficulty> difficulties,List<Integer> genreIds){
        public Song {difficulties=withUltimaSlot(difficulties);genreIds=List.copyOf(genreIds);}
    }
    private static final Map<ResourceManager,List<Chart>> CACHE=new WeakHashMap<>();
    private static final Map<ResourceManager,List<Song>> CATALOG=new WeakHashMap<>();
    private static final Map<ResourceManager,List<Genre>> GENRES=new WeakHashMap<>();
    private SongLibrary(){}
    public static List<Genre> readGenres(JsonObject data){
        var result=new ArrayList<Genre>();var ids=new HashSet<Integer>();var names=new HashSet<String>();
        for(var entry:data.getAsJsonArray("genres")){
            var genre=entry.getAsJsonObject();int id=genre.get("id").getAsInt();String name=genre.get("name").getAsString();
            if(id<0||id>499_998||name.isBlank()||!ids.add(id)||!names.add(name))throw new IllegalArgumentException("Invalid song genre registry");
            result.add(new Genre(id,name));
        }
        result.sort(Comparator.comparingInt(Genre::id));return List.copyOf(result);
    }
    public static List<Genre> genres(ServerLevel level){
        return GENRES.computeIfAbsent(level.getServer().getResourceManager(),manager->{
            try{return readGenres(read(manager,"minecraftchunithm:songs/genres.json"));}
            catch(IOException|RuntimeException error){MinecraftChunithm.LOGGER.error("Cannot load song genres",error);return List.of();}
        });
    }
    /** Existing song metadata stores the XML category names; the registry supplies their original IDs. */
    public static List<Integer> genreIds(JsonObject metadata,List<Genre> genres){
        var names=new HashSet<String>();var values=metadata.getAsJsonArray("genres");
        if(values!=null)for(var value:values)names.add(value.getAsString());
        return genres.stream().filter(genre->names.contains(genre.name())).map(Genre::id).toList();
    }
    public static List<Song> filter(List<Song> catalog,int genreId){
        return genreId==SongPanelMenu.ALL_GENRES?catalog:catalog.stream().filter(song->song.genreIds().contains(genreId)).toList();
    }
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
                    songs.add(new Song(songId,metadata.get("title").getAsString(),metadata.get("artist").getAsString(),metadata.getAsJsonObject("cover").get("texture").getAsString(),List.copyOf(difficulties),genreIds(metadata,genres(level))));
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
