package io.github.marconik.minecraftchunithm.client;

import com.google.gson.*;
import io.github.marconik.minecraftchunithm.*;
import java.io.IOException;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Local listening preferences, independent of player IDs and server permissions. */
final class AudioVolumeSettings {
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("minecraftchunithm-audio.json");
    private static volatile AudioVolume levels=AudioVolume.DEFAULT;
    private static long changed;
    private AudioVolumeSettings(){}
    static void initialize(){
        if(Files.exists(FILE))try(var reader=Files.newBufferedReader(FILE)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            levels=new AudioVolume(json.get("system").getAsDouble(),json.get("effects").getAsDouble());
        }catch(IOException|RuntimeException error){MinecraftChunithm.LOGGER.warn("Cannot read local audio preferences; using defaults",error);}
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(changed!=0&&System.nanoTime()-changed>250_000_000L)save();});
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->save());
    }
    static AudioVolume levels(){return levels;}
    static double gain(boolean effect){return levels.gain(effect);}
    static void set(boolean effect,double value){
        var next=effect?new AudioVolume(levels.system(),value):new AudioVolume(value,levels.effects());
        if(!next.equals(levels)){levels=next;changed=System.nanoTime();}
    }
    static void save(){
        if(changed==0)return;
        changed=0;
        try{
            Files.createDirectories(FILE.getParent());
            var temporary=FILE.resolveSibling(FILE.getFileName()+".tmp");
            var json=new JsonObject();json.addProperty("system",levels.system());json.addProperty("effects",levels.effects());
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(json)+"\n");
            try{Files.move(temporary,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,FILE,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException error){MinecraftChunithm.LOGGER.warn("Cannot save local audio preferences",error);}
    }
}
