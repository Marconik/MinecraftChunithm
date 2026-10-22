package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.MinecraftChunithm;
import net.fabricmc.api.ClientModInitializer;

public final class MinecraftChunithmClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.minecraft.client.gui.screens.MenuScreens.register(io.github.marconik.minecraftchunithm.HeadControlMenu.TYPE,HeadControlScreen::new);
        DevelopmentHeadUiCapture.initialize();
        MinecraftChunithm.LOGGER.info("MinecraftChunithm client initialized: build={}",MinecraftChunithm.BUILD_LABEL);
    }
}
