package io.github.marconik.minecraftchunithm;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;

/** Opt-in local smoke test for the existing development-only superflat world. */
public final class DevelopmentMotionTest {
    private static volatile boolean inputCheckComplete;
    private static volatile Placement inputHead;
    public static Placement inputHead(){return inputHead;}
    public static boolean inputCheckComplete(){return inputCheckComplete;}
    private DevelopmentMotionTest() {}

    public static void initialize() {
        boolean models=Boolean.getBoolean("minecraftchunithm.modelQuickPlay");
        boolean nameplates=Boolean.getBoolean("minecraftchunithm.nameplateQuickPlay");
        boolean connections=Boolean.getBoolean("minecraftchunithm.connectionQuickPlay");
        boolean inputs=Boolean.getBoolean("minecraftchunithm.inputQuickPlay");
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()
                || (!models && !nameplates && !connections && !inputs && !Boolean.getBoolean("minecraftchunithm.motionQuickPlay"))) return;
        int[] ticks = {0};
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getPlayerList().getPlayers().isEmpty() || ticks[0] >= 100) return;
            if (++ticks[0] != 100) return;
            var player = server.getPlayerList().getPlayers().getFirst();
            var source = player.createCommandSourceStack();
            var commands = server.getCommands();
            if(inputs){inputHead=DevelopmentInputCheck.run(source);inputCheckComplete=true;return;}
            if(connections){
                commands.performPrefixedCommand(source,"gamemode creative");
                commands.performPrefixedCommand(source,"time set day");
                commands.performPrefixedCommand(source,"tp @s 128.5 -60 0.5 180 0");
                var anchor=player.createCommandSourceStack();
                // Explicit test path may load its own small footprint before invoking normal preflight.
                for(int x=7;x<=9;x++)for(int z=-1;z<=2;z++)server.overworld().getChunk(x,z);
                if(PartAssembly.generate(anchor,24)!=1)throw new IllegalStateException("Connection assembly failed");
                var head=PartConnections.find(server.overworld(),PartConnections.Kind.HEAD,Placement.head(anchor));
                if(PartConnections.links(server.overworld(),head).stream().anyMatch(l->l.status()!=PartConnections.Status.CONNECTED))
                    throw new IllegalStateException("Assembly ports failed to connect");
                var track=PartConnections.find(server.overworld(),PartConnections.Kind.TRACK,PartConnections.target(head.placement,PartConnections.Kind.TRACK));
                var ground=PartConnections.find(server.overworld(),PartConnections.Kind.GROUND_SLIDE,PartConnections.groundTarget(track.placement,track.length));
                if(ground==null||PartConnections.integrity(server.overworld(),ground)!=PartConnections.Status.CONNECTED)
                    throw new IllegalStateException("Ground Slide failed to connect to the track end");
                commands.performPrefixedCommand(player.createCommandSourceStack(),"tp @s 128.5 -60 -3.5 0 7");
                PartWindows.open(player,server.overworld(),head,0);
                if(!(player.containerMenu instanceof HeadControlMenu menu)||menu.enabled(0)||!menu.enabled(1)||!menu.enabled(2))
                    throw new IllegalStateException("Native head menu or no-chart transport buttons were not initialized correctly");
                MinecraftChunithm.LOGGER.info("Connection smoke test passed: all five head ports and Ground Slide connected; head window opened.");
                return;
            }
            commands.performPrefixedCommand(source, "gamemode creative");
            if(nameplates){
                commands.performPrefixedCommand(source,"time set day");
                commands.performPrefixedCommand(source,"weather clear");
                for(int i=0;i<2;i++){
                    commands.performPrefixedCommand(source,"tp @s "+(64.5+i*8)+" -60 0.5 180 0");
                    var anchor=player.createCommandSourceStack();
                    commands.performPrefixedCommand(anchor,"chunithm debug nameplate");
                    NamePanel.update(server.overworld(),NamePanel.placement(anchor),new NameDisplayData(
                            "CHUNITHM TEAM",i==0?"SILVER TITLE":"GOLD TITLE",i==0?NameDisplayData.TitleColor.SILVER:NameDisplayData.TitleColor.GOLD,
                            "99","PLAYER "+(i+1),NameDisplayData.defaults().characterModel(),"16.50"));
                }
                commands.performPrefixedCommand(player.createCommandSourceStack(),"tp @s 65.5 -58.5 9 180 8");
                MinecraftChunithm.LOGGER.info("Nameplate showcase ready: silver and gold panels, camera 65.5 -58.5 9.");
                return;
            }
            if(models) {
                commands.performPrefixedCommand(source.withPosition(new Vec3(0,-60,0)),
                        "function minecraftchunithm:air_showcase");
                commands.performPrefixedCommand(source,"tp @s 4.5 -57 24 180 20");
                MinecraftChunithm.LOGGER.info("AIR model showcase loaded: six thin AIR arrows, three square AIR-ACTION paths, latest resources.");
                return;
            }
            commands.performPrefixedCommand(source, "tp @s 4.5 -54 28 180 30");
            commands.performPrefixedCommand(source.withPosition(new Vec3(0,-60,0)), "chunithm debug test 24 120 10");
            MinecraftChunithm.LOGGER.info("Development motion test requested: New World, origin 0 -61 0, length 24, BPM 120, speed 10.");
        });
    }
}
