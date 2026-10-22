package io.github.marconik.minecraftchunithm;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinecraftChunithm implements ModInitializer {
    public static final String MOD_ID = "minecraftchunithm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModModels.initialize();
        TrackHead.initialize();
        SongPanel.initialize();
        TrackCommands.initialize();
        TrackMotion.initialize();
        DevelopmentMotionTest.initialize();
        LOGGER.info("MinecraftChunithm initialized: track blocks and 16 TAP model widths.");
    }
}
