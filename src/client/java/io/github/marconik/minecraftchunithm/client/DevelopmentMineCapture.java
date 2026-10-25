package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;

/** Native audio checks and screenshots for the opt-in isolated mine fixture. */
final class DevelopmentMineCapture {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.mineQuickPlay"))return;
        boolean[] checked=new boolean[5];boolean[] closed={false};int[] stopTicks={0};long[] triggers={0};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!DevelopmentMotionTest.inputCheckComplete()||client.player==null)return;
            if(!closed[0]){client.player.closeContainer();if(!client.gui.hud.isHidden())client.gui.hud.toggle();closed[0]=true;}
            int phase=DevelopmentMineCheck.phase();double time=DevelopmentMineCheck.time();
            if(phase==0&&client.gui.screen()!=null)client.player.closeContainer();
            if(phase==1&&!checked[1]&&time>=1050&&time<=1150&&NoteCuePlayback.checkTime()>=1050){
                NoteCuePlayback.verifyNative(true);
                if(NoteCuePlayback.checkTriggers()!=5)throw new AssertionError("MNE-only chart must trigger exactly five paired Tap cues");
                if(NoteCuePlayback.checkReplacements()<2)throw new AssertionError("Dense mine cues must replace the previous sound");
                triggers[0]=NoteCuePlayback.checkTriggers();checked[1]=true;DevelopmentMineCheck.acknowledge();
            }
            if(phase>=2&&phase<=4&&!checked[phase]&&time>=(phase==3?1750:2500)){
                RailMusicPlayback.verifyNative(true,1000);
                if(NoteCuePlayback.checkTriggers()<=triggers[0])throw new AssertionError("Real song playback did not emit note cues");
                triggers[0]=NoteCuePlayback.checkTriggers();checked[phase]=true;
                Screenshot.grab(client.gameDirectory,"melodiniq-"+(phase==2?"master":phase==3?"ultima":"ultima-mirror")+".png",client.gameRenderer.mainRenderTarget(),1,
                        message->MinecraftChunithm.LOGGER.info("Melodiniq capture: {}",message.getString()));
                DevelopmentMineCheck.acknowledge();
            }
            if(DevelopmentMineCheck.finished()&&++stopTicks[0]>=10){
                NoteCuePlayback.verifyNative(false);RailMusicPlayback.verifyNative(false,0);
                try{Files.writeString(client.gameDirectory.toPath().resolve("melodiniq-mine-checks.json"),DevelopmentMineCheck.result());}
                catch(java.io.IOException error){throw new RuntimeException(error);}
                if(client.gui.hud.isHidden())client.gui.hud.toggle();client.stop();
            }
        });
    }
}
