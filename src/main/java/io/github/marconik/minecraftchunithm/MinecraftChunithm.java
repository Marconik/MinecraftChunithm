package io.github.marconik.minecraftchunithm;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinecraftChunithm implements ModInitializer {
    public static final String MOD_ID = "minecraftchunithm";
    public static final String BUILD_LABEL = "gradient-gold-metal-gloss-4";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CabinetBlocks.initialize();
        ModModels.initialize();
        GroundSlide.initialize();
        AirSensors.initialize();
        TrackHead.initialize();
        TrackHeadDisplay.initialize();
        SongPanel.initialize();
        NamePanel.initialize();
        PartPorts.initialize();
        TrackCommands.initialize();
        PartWindows.initialize();
        HeadControlMenu.initialize();
        SongPanelMenu.initialize();
        PlayerPanelMenu.initialize();
        MusicQueue.initialize();
        TrackMotion.initialize();
        RailAudio.initialize();
        NoteCueAudio.initialize();
        ChartDisplayPose.initialize();
        ChartPlayback.initialize();
        WorldUninstall.initialize();
        DevelopmentMotionTest.initialize();
        DevelopmentDifferentialCheck.initialize();
        DevelopmentMineCheck.initialize();
        DevelopmentScoreCheck.initialize();
        DevelopmentUninstallCheck.initialize();
        DevelopmentOffTrackCheck.initialize();
        LOGGER.info("MinecraftChunithm initialized: build={}, control logo registered={}",BUILD_LABEL,PartPorts.ready());
    }
}
