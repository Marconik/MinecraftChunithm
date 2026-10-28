package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;

final class DevelopmentOffTrackCapture {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.offTrackQuickPlay"))return;
        int[] waiting={0};boolean[] closed={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null)return;
            if(client.gui.screen()==null){
                DevelopmentOffTrackCheck.ready();var camera=DevelopmentOffTrackCheck.camera();
                if(camera!=null&&client.player.position().distanceTo(camera)<1)DevelopmentOffTrackCheck.acknowledgeCamera();
            }
            if(!closed[0]){client.player.closeContainer();if(!client.gui.hud.isHidden())client.gui.hud.toggle();closed[0]=true;}
            if(DevelopmentOffTrackCheck.capture()){
                if(++waiting[0]>=15){
                    int phase=DevelopmentOffTrackCheck.phase();
                    Screenshot.grab(client.gameDirectory,"melodiniq-off-track-"+phase+".png",client.gameRenderer.mainRenderTarget(),1,
                            message->MinecraftChunithm.LOGGER.info("Off-track capture: {}",message.getString()));
                    DevelopmentOffTrackCheck.acknowledge();waiting[0]=0;
                }
            }
            if(DevelopmentOffTrackCheck.result()!=null){
                if(ChartDisplayInterpolation.checked()==0)throw new AssertionError("Chart renderer hook never inspected client subframes");
                var report=com.google.gson.JsonParser.parseString(DevelopmentOffTrackCheck.result()).getAsJsonObject();
                report.addProperty("clientRenderSubframes",ChartDisplayInterpolation.checked());
                try{Files.writeString(client.gameDirectory.toPath().resolve("melodiniq-off-track-checks.json"),report.toString());}
                catch(java.io.IOException error){throw new RuntimeException(error);}
                client.stop();
            }
        });
    }
}
