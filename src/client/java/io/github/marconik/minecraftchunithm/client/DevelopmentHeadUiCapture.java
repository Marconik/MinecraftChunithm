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
            if(inputs&&(tick==90||tick==120)){
                if(!client.gui.hud.isHidden())client.gui.hud.toggle();
                var kind=tick==90?PartConnections.Kind.PLAYER:PartConnections.Kind.SONG;
                var server=client.getSingleplayerServer();
                if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();
                    var p=PartConnections.target(DevelopmentMotionTest.inputHead(),kind);
                    var camera=p.point(3.5,0,-3);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),
                            "tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+p.forward().toYRot()+" 3");
                });
            }
            if(inputs&&(tick==110||tick==140))Screenshot.grab(client.gameDirectory,
                    tick==110?"player-terminal-recess.png":"song-terminal-recess.png",client.gameRenderer.mainRenderTarget(),1,
                    message->MinecraftChunithm.LOGGER.info("Side terminal capture: {}",message.getString()));
            if(inputs&&(tick==150||tick==180||tick==210||tick==240)){
                var server=client.getSingleplayerServer();
                if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();
                    var head=DevelopmentMotionTest.inputHead();
                    var p=tick==210?PartConnections.target(head,PartConnections.Kind.PLAYER):
                            tick==240?PartConnections.target(head,PartConnections.Kind.SONG):head;
                    var camera=tick==180?p.point(11.5,2.8,2):p.point(tick>=210?3.5:5.5,tick>=210?.3:1.5,tick>=210?5:9);
                    var target=p.point(tick==180?8.75:tick>=210?3.5:5.5,tick==180?3.5625:tick>=210?2:2,1);
                    double dx=target.x-camera.x,dy=target.y-(camera.y+player.getEyeHeight()),dz=target.z-camera.z;
                    double yaw=Math.toDegrees(Math.atan2(-dx,dz)),pitch=Math.toDegrees(Math.atan2(-dy,Math.hypot(dx,dz)));
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),
                            "tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+yaw+" "+pitch);
                });
            }
            if(inputs&&(tick==170||tick==200||tick==230||tick==260))Screenshot.grab(client.gameDirectory,
                    tick==170?"head-progress-fixed.png":tick==200?"head-surface-oblique.png":tick==230?"player-front-surface.png":"song-front-surface.png",
                    client.gameRenderer.mainRenderTarget(),1,message->MinecraftChunithm.LOGGER.info("Facade capture: {}",message.getString()));
            if(inputs&&tick==280){if(client.gui.hud.isHidden())client.gui.hud.toggle();client.stop();}
        });
    }
}
