package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;

/** Native framebuffer captures for the explicit local connection smoke test only. */
final class DevelopmentHeadUiCapture {
    static void initialize(){
        if(Boolean.getBoolean("minecraftchunithm.noteCueQuickPlay"))return;
        boolean inputs=Boolean.getBoolean("minecraftchunithm.inputQuickPlay");
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||(!inputs&&!Boolean.getBoolean("minecraftchunithm.connectionQuickPlay")))return;
        boolean ultimaOnly=Boolean.getBoolean("minecraftchunithm.ultimaQuickPlay");
        boolean decorations=Boolean.getBoolean("minecraftchunithm.decorationQuickPlay");
        boolean theatore=Boolean.getBoolean("minecraftchunithm.theatoreQuickPlay");
        String songPrefix=theatore?"theatore":"younithm";
        int[] ticks={decorations?999:ultimaOnly||theatore?659:0};boolean[] started={false};
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
            if(inputs&&(tick==150||tick==180||tick==210||tick==240||tick==480)){
                var server=client.getSingleplayerServer();
                if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();
                    var head=DevelopmentMotionTest.inputHead();
                    var p=tick==210?PartConnections.target(head,PartConnections.Kind.PLAYER):
                            tick==240||tick==480?PartConnections.target(head,PartConnections.Kind.SONG):head;
                    if(tick==240){
                        var defaults=SongDisplayData.defaults();
                        SongPanel.update(player.level(),p,new SongDisplayData(defaults.coverModel(),
                                "エンドマークに希望と涙を添えて / 微软雅黑 Scrolling Song Title Preview",
                                "光吉猛修 / Microsoft YaHei Regular Font / Scrolling Artist Preview",1,defaults.difficulty(),defaults.level(),10,false));
                    }
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
            if(inputs&&tick==270){
                var server=client.getSingleplayerServer();
                if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();
                    var sensor=PartConnections.inputRoute(player.level(),DevelopmentMotionTest.inputHead()).leftAir().part().placement;
                    var camera=sensor.point(9,1.5,4);var target=sensor.point(1,2.5,1.5);
                    double dx=target.x-camera.x,dy=target.y-(camera.y+player.getEyeHeight()),dz=target.z-camera.z;
                    double yaw=Math.toDegrees(Math.atan2(-dx,dz)),pitch=Math.toDegrees(Math.atan2(-dy,Math.hypot(dx,dz)));
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),
                            "tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+yaw+" "+pitch);
                });
            }
            if(inputs&&tick==300)Screenshot.grab(client.gameDirectory,"air-sensor-raised-glow.png",client.gameRenderer.mainRenderTarget(),1,
                    message->MinecraftChunithm.LOGGER.info("AIR raised housing capture: {}",message.getString()));
            if(inputs&&(tick==310||tick==380||tick==400||tick==430||tick==460)){
                var server=client.getSingleplayerServer();
                if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();var source=player.createCommandSourceStack();
                    var commands=server.getCommands();
                    if(tick==310){
                        var track=PartConnections.inputRoute(player.level(),DevelopmentMotionTest.inputHead()).track().part();
                        var p=track.placement.point(4.5,1,track.length+1);
                        commands.performPrefixedCommand(source,"tp @s "+p.x+" "+p.y+" "+p.z+" "+track.placement.forward().getOpposite().toYRot()+" 10");
                        commands.performPrefixedCommand(player.createCommandSourceStack(),"chunithm chart play 103 10 false");
                    }else commands.performPrefixedCommand(source,"chunithm chart "+(tick==380?"pause":tick==400?"resume":tick==430?"seek 60":"stop"));
                });
            }
            if(inputs&&(tick==370||tick==420||tick==450))RailMusicPlayback.verifyNative(true,tick==450?60000:0);
            if(inputs&&(tick==390||tick==470))RailMusicPlayback.verifyNative(false,0);
            if(inputs&&tick==510){
                SongPanelTextRenderer.verifyMarquee();
                Screenshot.grab(client.gameDirectory,"song-panel-yahei-scroll.png",client.gameRenderer.mainRenderTarget(),1,
                        message->MinecraftChunithm.LOGGER.info("YaHei marquee capture: {}",message.getString()));
            }
            if(inputs&&(tick==520||tick==590)){
                var server=client.getSingleplayerServer();
                if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();
                    if(tick==520)DevelopmentInputCheck.showAirActions(player.createCommandSourceStack());
                    var p=PartConnections.inputRoute(player.level(),DevelopmentMotionTest.inputHead()).track().part().placement;
                    var camera=p.point(tick==520?4.5:9.5,.6,tick==520?9:7);var target=p.point(4.5,1.2,1.8);
                    double dx=target.x-camera.x,dy=target.y-(camera.y+player.getEyeHeight()),dz=target.z-camera.z;
                    double yaw=Math.toDegrees(Math.atan2(-dx,dz)),pitch=Math.toDegrees(Math.atan2(-dy,Math.hypot(dx,dz)));
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),
                            "tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+yaw+" "+pitch);
                });
            }
            if(inputs&&(tick==560||tick==630))Screenshot.grab(client.gameDirectory,tick==560?"air-action-front.png":"air-action-oblique.png",
                    client.gameRenderer.mainRenderTarget(),1,message->MinecraftChunithm.LOGGER.info("Air Action capture: {}",message.getString()));
            if(inputs&&(tick==660||tick==720)){
                if((ultimaOnly||theatore)&&tick==660&&client.player!=null){client.player.closeContainer();if(!client.gui.hud.isHidden())client.gui.hud.toggle();}
                var server=client.getSingleplayerServer();
                if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();var source=player.createCommandSourceStack();
                    DevelopmentInputCheck.showSongDifficulties(source,theatore?2712:2967,tick==660?"MASTER":"ULTIMA");
                    var p=PartConnections.target(DevelopmentMotionTest.inputHead(),PartConnections.Kind.SONG);
                    var camera=p.point(3.5,.3,5);var target=p.point(3.5,2,1);
                    double dx=target.x-camera.x,dy=target.y-(camera.y+player.getEyeHeight()),dz=target.z-camera.z;
                    server.getCommands().performPrefixedCommand(source,"tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+
                            Math.toDegrees(Math.atan2(-dx,dz))+" "+Math.toDegrees(Math.atan2(-dy,Math.hypot(dx,dz))));
                });
            }
            if(inputs&&(tick==700||tick==760||tick==840||tick==880))Screenshot.grab(client.gameDirectory,
                    songPrefix+(tick==700?"-master-panel.png":tick==760?"-ultima-panel.png":tick==840?"-difficulty-selector.png":"-selected-queue.png"),
                    client.gameRenderer.mainRenderTarget(),1,message->MinecraftChunithm.LOGGER.info("YOUNITHM capture: {}",message.getString()));
            if(inputs&&(tick==680||tick==690)){
                var server=client.getSingleplayerServer();if(server!=null)server.execute(()->{
                    var source=server.getPlayerList().getPlayers().getFirst().createCommandSourceStack();
                    server.getCommands().performPrefixedCommand(source,tick==680?"chunithm chart play":"chunithm chart seek 60");
                });
            }
            if(inputs&&tick==715)RailMusicPlayback.verifyNative(true,60000);
            if(inputs&&tick==770){var server=client.getSingleplayerServer();if(server!=null)server.execute(()->
                    DevelopmentInputCheck.openYounithmPanel(server.getPlayerList().getPlayers().getFirst().createCommandSourceStack()));}
            if(inputs&&tick==790&&client.gui.screen() instanceof SongPanelScreen screen){
                var button=screen.children().stream().filter(widget->widget instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("选择 TRACK 1"))
                        .map(widget->(net.minecraft.client.gui.components.Button)widget).findFirst().orElseThrow();button.onPress(null);
            }
            if(inputs&&tick==800&&client.gui.screen() instanceof SongPanelScreen screen)
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId,SongPanelMenu.scrollAction(Math.max(0,screen.getMenu().state().total()-3)));
            if(inputs&&tick==860&&client.gui.screen() instanceof SongPanelScreen screen){
                var button=screen.children().stream().filter(widget->widget instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith(theatore?"ULTIMA 15.3":"ULTIMA 15.8"))
                        .map(widget->(net.minecraft.client.gui.components.Button)widget).findFirst().orElseThrow();
                if(!button.active)throw new AssertionError("Imported ULTIMA difficulty is disabled");button.onPress(null);
            }
            if(inputs&&tick==890&&client.player!=null)client.player.closeContainer();
            if(inputs&&(tick==900||tick==910||tick==960)){
                var server=client.getSingleplayerServer();if(server!=null)server.execute(()->{
                    var source=server.getPlayerList().getPlayers().getFirst().createCommandSourceStack();
                    server.getCommands().performPrefixedCommand(source,tick==900?"chunithm chart play \"minecraftchunithm:songs/"+(theatore?"2712":"2967")+"/ultima.json\" 30 false":
                            tick==910?"chunithm chart seek 140":"chunithm chart pause");
                });
            }
            if(inputs&&tick==950)RailMusicPlayback.verifyNative(true,140000);
            if(inputs&&tick==980)RailMusicPlayback.verifyNative(false,0);
            if(inputs&&decorations&&(tick==1000||tick==1080||tick==1160)){
                if(client.player!=null)client.player.closeContainer();if(!client.gui.hud.isHidden())client.gui.hud.toggle();
                var server=client.getSingleplayerServer();if(server!=null)server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();
                    DevelopmentInputCheck.showDecorativeLines(player.createCommandSourceStack(),tick==1000?0:tick==1080?2967:2475);
                    var p=PartConnections.inputRoute(player.level(),DevelopmentMotionTest.inputHead()).track().part().placement;
                    var camera=p.point(7,1.4,7);var target=p.point(4.5,1.3,2);
                    double dx=target.x-camera.x,dy=target.y-(camera.y+player.getEyeHeight()),dz=target.z-camera.z;
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+
                            Math.toDegrees(Math.atan2(-dx,dz))+" "+Math.toDegrees(Math.atan2(-dy,Math.hypot(dx,dz))));
                });
            }
            if(inputs&&decorations&&(tick==1050||tick==1130||tick==1210))Screenshot.grab(client.gameDirectory,
                    tick==1050?"decoration-glow-palette.png":tick==1130?"younithm-decoration-glow.png":"chaotic-order-decoration-glow.png",
                    client.gameRenderer.mainRenderTarget(),1,message->MinecraftChunithm.LOGGER.info("Decoration glow capture: {}",message.getString()));
            if(inputs&&tick==(decorations?1240:1000)){if(client.gui.hud.isHidden())client.gui.hud.toggle();client.stop();}
        });
    }
}
