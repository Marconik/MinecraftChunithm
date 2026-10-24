package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;

/** Native framebuffer captures for the explicit local connection smoke test only. */
final class DevelopmentHeadUiCapture {
    static void initialize(){
        boolean inputs=Boolean.getBoolean("minecraftchunithm.inputQuickPlay");
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||(!inputs&&!Boolean.getBoolean("minecraftchunithm.connectionQuickPlay")))return;
        int[] ticks={0};boolean[] started={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!started[0]){
                if(inputs&&!DevelopmentMotionTest.inputCheckComplete())return;
                if(!(client.gui.screen() instanceof HeadControlScreen screen)||!screen.getMenu().connected(HeadControlMenu.TRACK))return;
                started[0]=true;
                MinecraftChunithm.LOGGER.info("Native head screen active: build={}, white wide title; centered 16-lane bank",MinecraftChunithm.BUILD_LABEL);
            }
            int tick=++ticks[0];
            if(inputs&&tick==15&&client.gui.screen() instanceof HeadControlScreen screen){
                var messages=screen.children().stream().filter(widget->widget instanceof net.minecraft.client.gui.components.Button)
                        .map(widget->((net.minecraft.client.gui.components.Button)widget).getMessage().getString()).toList();
                if(!messages.contains("Right Air\n已连接")||!messages.contains("Left Air\n已连接"))
                    throw new AssertionError("AIR tooltips must use mirrored player-side names and the short connected status");
                MinecraftChunithm.LOGGER.info("AIR tooltip check passed: window left=Right Air; window right=Left Air; connected status has no suffix.");
            }
            if(tick==30&&client.gui.screen() instanceof HeadControlScreen){
                if(inputs&&client.gui.screen() instanceof HeadControlScreen screen
                        &&(!screen.getMenu().connected(HeadControlMenu.AIR_LEFT)||!screen.getMenu().connected(HeadControlMenu.AIR_RIGHT)
                        ||!screen.getMenu().connected(HeadControlMenu.GROUND)))throw new AssertionError("Input connectivity was lost in native menu synchronization");
                Screenshot.grab(client.gameDirectory,inputs?"air-input-connected.png":"head-control-ui.png",client.gameRenderer.mainRenderTarget(),1,
                        message->MinecraftChunithm.LOGGER.info("Head UI capture: {}",message.getString()));
            }
            if(tick==45&&client.gui.screen() instanceof HeadControlScreen&&client.player!=null)client.player.closeContainer();
            if(tick==75&&client.gui.screen()==null){
                Screenshot.grab(client.gameDirectory,inputs?"head-terminal-recess.png":"head-control-logo.png",client.gameRenderer.mainRenderTarget(),1,
                        message->MinecraftChunithm.LOGGER.info("Head rear logo capture: {}",message.getString()));
            }
            if(inputs&&tick==90)client.stop();
        });
    }
}
