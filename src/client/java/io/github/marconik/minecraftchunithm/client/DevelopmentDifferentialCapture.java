package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;

/** Captures the real imported mu3 passage and validates its independent music clock. */
final class DevelopmentDifferentialCapture {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.differentialQuickPlay"))return;
        boolean[] active={false,false},checkedPause={false,false},closed={false};int[] pauseTicks={0,0};boolean[][] capture=new boolean[2][2];
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!DevelopmentMotionTest.inputCheckComplete()||client.player==null)return;
            if(!closed[0]){client.player.closeContainer();if(!client.gui.hud.isHidden())client.gui.hud.toggle();closed[0]=true;}
            int pass=DevelopmentDifferentialCheck.pass();double time=DevelopmentDifferentialCheck.time();
            if(time>=124000&&!active[pass]&&!DevelopmentDifferentialCheck.waiting()){
                RailMusicPlayback.verifyNative(true,123000);active[pass]=true;
            }
            for(int i=0;i<2;i++)if(time>=(i==0?124850:128550)&&!capture[pass][i]){
                capture[pass][i]=true;
                Screenshot.grab(client.gameDirectory,"mu3-"+(pass==0?"normal":"mirror")+"-"+i+".png",client.gameRenderer.mainRenderTarget(),1,
                        message->MinecraftChunithm.LOGGER.info("mu3 differential capture: {}",message.getString()));
            }
            if(DevelopmentDifferentialCheck.waiting()&&!checkedPause[pass]&&++pauseTicks[pass]>=10){
                if(!active[pass])throw new AssertionError("mu3 music never started");
                RailMusicPlayback.verifyNative(false,0);NoteCuePlayback.verifyNative(false);checkedPause[pass]=true;DevelopmentDifferentialCheck.acknowledgeAudio();
            }
            if(DevelopmentDifferentialCheck.finished()){
                try{Files.writeString(client.gameDirectory.toPath().resolve("mu3-differential-checks.json"),DevelopmentDifferentialCheck.result());}
                catch(java.io.IOException error){throw new RuntimeException(error);}
                if(client.gui.hud.isHidden())client.gui.hud.toggle();client.stop();
            }
        });
    }
}
