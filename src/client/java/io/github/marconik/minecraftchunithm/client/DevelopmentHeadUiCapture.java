package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;

/** Native framebuffer captures for the explicit local connection smoke test only. */
final class DevelopmentHeadUiCapture {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.connectionQuickPlay"))return;
        int[] ticks={0};boolean[] started={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!started[0]){
                if(!(client.gui.screen() instanceof HeadControlScreen screen)||!screen.getMenu().connected(HeadControlMenu.TRACK))return;
                started[0]=true;
                MinecraftChunithm.LOGGER.info("Native head screen active: build={}, white wide title; centered 16-lane bank",MinecraftChunithm.BUILD_LABEL);
            }
            int tick=++ticks[0];
            if(tick==30&&client.gui.screen() instanceof HeadControlScreen){
                Screenshot.grab(client.gameDirectory,"head-control-ui.png",client.gameRenderer.mainRenderTarget(),1,
                        message->MinecraftChunithm.LOGGER.info("Head UI capture: {}",message.getString()));
            }
            if(tick==45&&client.gui.screen() instanceof HeadControlScreen&&client.player!=null)client.player.closeContainer();
            if(tick==75&&client.gui.screen()==null){
                Screenshot.grab(client.gameDirectory,"head-control-logo.png",client.gameRenderer.mainRenderTarget(),1,
                        message->MinecraftChunithm.LOGGER.info("Head rear logo capture: {}",message.getString()));
            }
        });
    }
}
