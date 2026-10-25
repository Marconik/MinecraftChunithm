package io.github.marconik.minecraftchunithm;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;

/** Data-pack-backed player profiles available to the player-information panel. */
public final class PlayerLibrary {
    public record Profile(String id,String name,String characterId,int level,BigDecimal rating,
                          String title,NameDisplayData.TitleColor titleColor,String team) {
        public Profile {
            if(id==null||!id.matches("\\d{16}")||name==null||name.isBlank()||name.length()>256
                    ||characterId==null||!characterId.matches("\\d{4}")||level<1||level>99
                    ||rating==null||rating.signum()<0||rating.scale()>2||title==null||title.length()>256
                    ||titleColor==null||team==null||team.length()>256||hasLineBreak(name,title,team))
                throw new IllegalArgumentException("Invalid player profile: "+id);
        }
        private static boolean hasLineBreak(String... values){
            for(String value:values)if(value.contains("\n")||value.contains("\r"))return true;
            return false;
        }
    }
    private static final Identifier INDEX=Identifier.parse("minecraftchunithm:players/index.json");
    private static final Map<ResourceManager,Map<String,Profile>> CACHE=new WeakHashMap<>();
    private PlayerLibrary(){}

    public static List<Profile> all(ServerLevel level){return List.copyOf(load(level).values());}
    public static Profile find(ServerLevel level,String id){return id==null?null:load(level).get(id);}
    public static NameDisplayData display(ServerLevel level,Profile profile){
        var avatar=CharacterLibrary.find(level,profile.characterId());
        if(avatar==null)return null;
        return new NameDisplayData(profile.team(),profile.title(),profile.titleColor(),Integer.toString(profile.level()),
                profile.name(),Identifier.parse(avatar.itemModel()),profile.rating().setScale(2).toPlainString());
    }
    public static List<NameDisplayData> displayChoices(ServerPlayer player){
        ServerLevel level=(ServerLevel)player.level();List<NameDisplayData> result=new ArrayList<>();
        for(var profile:all(level)){
            var display=display(level,profile);
            if(display==null){MinecraftChunithm.LOGGER.warn("Player {} references missing character {}",profile.id(),profile.characterId());continue;}
            result.add(display);
        }
        return List.copyOf(result);
    }
    private static Map<String,Profile> load(ServerLevel level){
        var manager=level.getServer().getResourceManager();
        return CACHE.computeIfAbsent(manager,PlayerLibrary::read);
    }
    private static Map<String,Profile> read(ResourceManager manager){
        Map<String,Profile> result=new LinkedHashMap<>();
        try(var reader=manager.getResourceOrThrow(INDEX).openAsReader()){
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("formatVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported player index version");
            for(var value:root.getAsJsonArray("players")){
                var json=value.getAsJsonObject();
                var profile=new Profile(json.get("id").getAsString(),json.get("name").getAsString(),
                        json.get("characterId").getAsString(),json.get("level").getAsInt(),
                        new BigDecimal(json.get("rating").getAsString()),json.get("title").getAsString(),
                        NameDisplayData.TitleColor.valueOf(json.get("titleColor").getAsString()),json.get("team").getAsString());
                if(result.put(profile.id(),profile)!=null)throw new IllegalArgumentException("Duplicate player ID: "+profile.id());
            }
        }catch(IOException|RuntimeException error){
            MinecraftChunithm.LOGGER.error("Cannot load player profile index",error);return Map.of();
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
