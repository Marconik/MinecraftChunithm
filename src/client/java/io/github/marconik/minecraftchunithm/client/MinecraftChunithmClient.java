package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.MinecraftChunithm;
import net.fabricmc.api.ClientModInitializer;

public final class MinecraftChunithmClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MinecraftChunithm.LOGGER.info("MinecraftChunithm client initialized.");
    }
}
