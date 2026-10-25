package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;

final class DevelopmentScoreCapture {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.scoreQuickPlay"))return;
        boolean[] captured=new boolean[8],hidden={false};int[] ticks=new int[8];
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!DevelopmentMotionTest.inputCheckComplete()||client.player==null)return;
            int phase=DevelopmentScoreCheck.phase();
            if(phase==0&&client.gui.screen()!=null)client.player.closeContainer();
            if(!hidden[0]){if(!client.gui.hud.isHidden())client.gui.hud.toggle();hidden[0]=true;}
            if((phase==2||phase==6)&&!captured[phase]&&++ticks[phase]>=5){
                captured[phase]=true;
                Screenshot.grab(client.gameDirectory,"automatic-score-"+(phase==2?"partial":"maximum")+".png",client.gameRenderer.mainRenderTarget(),1,
                    message->MinecraftChunithm.LOGGER.info("Automatic score capture: {}",message.getString()));
                DevelopmentScoreCheck.acknowledge();
            }
            if(DevelopmentScoreCheck.finished()){
                try{Files.writeString(client.gameDirectory.toPath().resolve("automatic-score-checks.json"),DevelopmentScoreCheck.result());}
                catch(java.io.IOException error){throw new RuntimeException(error);}
                if(client.gui.hud.isHidden())client.gui.hud.toggle();client.stop();
            }
        });
    }
}
