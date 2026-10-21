package io.github.marconik.minecraftchunithm;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;

/** Opt-in local smoke test for the existing development-only superflat world. */
public final class DevelopmentMotionTest {
    private DevelopmentMotionTest() {}

    public static void initialize() {
        boolean models=Boolean.getBoolean("minecraftchunithm.modelQuickPlay");
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()
                || (!models && !Boolean.getBoolean("minecraftchunithm.motionQuickPlay"))) return;
        int[] ticks = {0};
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getPlayerList().getPlayers().isEmpty() || ticks[0] >= 100) return;
            if (++ticks[0] != 100) return;
            var player = server.getPlayerList().getPlayers().getFirst();
            var source = player.createCommandSourceStack();
            var commands = server.getCommands();
            commands.performPrefixedCommand(source, "gamemode spectator");
            if(models) {
                commands.performPrefixedCommand(source.withPosition(new Vec3(0,-60,0)),
                        "function minecraftchunithm:air_showcase");
                commands.performPrefixedCommand(source,"tp @s 4.5 -57 24 180 20");
                MinecraftChunithm.LOGGER.info("AIR model showcase loaded: six thin AIR arrows, three square AIR-ACTION paths, latest resources.");
                return;
            }
            commands.performPrefixedCommand(source, "tp @s 4.5 -54 28 180 30");
            commands.performPrefixedCommand(source.withPosition(new Vec3(0,-60,0)), "chunithm test 24 120 10");
            MinecraftChunithm.LOGGER.info("Development motion test requested: New World, origin 0 -61 0, length 24, BPM 120, speed 10.");
        });
    }
}
