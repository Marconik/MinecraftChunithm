package io.github.marconik.minecraftchunithm;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;

/** Data-pack-backed character portrait index used by player-information features. */
public final class CharacterLibrary {
    public record Avatar(String id,String texture,String itemModel,int width,int height){}
    private static final Identifier INDEX=Identifier.parse("minecraftchunithm:characters/index.json");
    private static final Map<ResourceManager,Map<String,Avatar>> CACHE=new WeakHashMap<>();
    private CharacterLibrary(){}

    public static List<Avatar> all(ServerLevel level){return List.copyOf(load(level).values());}
    public static Avatar find(ServerLevel level,String id){
        if(id==null||!id.matches("\\d{4}"))return null;
        return load(level).get(id);
    }
    public static Avatar find(ServerLevel level,int id){
        if(id<0||id>9999)return null;
        return find(level,String.format(java.util.Locale.ROOT,"%04d",id));
    }
    private static Map<String,Avatar> load(ServerLevel level){
        var manager=level.getServer().getResourceManager();
        return CACHE.computeIfAbsent(manager,CharacterLibrary::read);
    }
    private static Map<String,Avatar> read(ResourceManager manager){
        Map<String,Avatar> result=new LinkedHashMap<>();
        try(var reader=manager.getResourceOrThrow(INDEX).openAsReader()){
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("formatVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported character index version");
            for(var value:root.getAsJsonArray("characters")){
                var json=value.getAsJsonObject();
                String id=json.get("id").getAsString(),texture=json.get("texture").getAsString(),itemModel=json.get("itemModel").getAsString();
                int width=json.get("width").getAsInt(),height=json.get("height").getAsInt();
                if(!id.matches("\\d{4}")||width<=0||height<=0)throw new IllegalArgumentException("Invalid character avatar entry: "+id);
                Identifier.parse(texture);Identifier.parse(itemModel);
                if(result.put(id,new Avatar(id,texture,itemModel,width,height))!=null)throw new IllegalArgumentException("Duplicate character ID: "+id);
            }
        }catch(IOException|RuntimeException error){
            MinecraftChunithm.LOGGER.error("Cannot load character avatar index",error);
            return Map.of();
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
