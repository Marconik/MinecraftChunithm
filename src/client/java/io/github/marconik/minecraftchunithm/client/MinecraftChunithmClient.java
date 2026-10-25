package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.MinecraftChunithm;
import net.fabricmc.api.ClientModInitializer;

public final class MinecraftChunithmClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RailMusicPlayback.initialize();
        NoteCuePlayback.initialize();
        SongPanelTextRenderer.initialize();
        net.minecraft.client.gui.screens.MenuScreens.register(io.github.marconik.minecraftchunithm.HeadControlMenu.TYPE,HeadControlScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(io.github.marconik.minecraftchunithm.SongPanelMenu.TYPE,SongPanelScreen::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            io.github.marconik.minecraftchunithm.SongPanelMenu.Snapshot.TYPE,(payload,context)->context.client().execute(()->{
                var player=context.client().player;
                if(player!=null&&player.containerMenu instanceof io.github.marconik.minecraftchunithm.SongPanelMenu menu&&menu.containerId==payload.containerId())menu.receive(payload.json());
            }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            io.github.marconik.minecraftchunithm.MusicQueue.Snapshot.TYPE,(payload,context)->context.client().execute(()->{
                var player=context.client().player;
                if(player!=null&&player.containerMenu instanceof io.github.marconik.minecraftchunithm.HeadControlMenu menu&&menu.containerId==payload.containerId())
                    menu.receiveMusic(payload.json());
            }));
        DevelopmentHeadUiCapture.initialize();
        DevelopmentNoteCueCheck.initialize();
        DevelopmentMusicUiPreview.initialize();
        MinecraftChunithm.LOGGER.info("MinecraftChunithm client initialized: build={}",MinecraftChunithm.BUILD_LABEL);
    }
}
