package io.github.marconik.minecraftchunithm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class TrackCommands {
    private TrackCommands() {}

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(tree()));
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tree(){
        return Commands.literal("chunithm")
                        .requires(source->Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)&&!WorldUninstall.running(source.getServer()))
                        .then(Commands.literal("uninstall").executes(context->WorldUninstall.start(context.getSource())))
                        .then(Commands.literal("assemble").executes(c->PartAssembly.generate(c.getSource(),24))
                                .then(Commands.argument("length",IntegerArgumentType.integer(2,1024))
                                        .executes(c->PartAssembly.generate(c.getSource(),IntegerArgumentType.getInteger(c,"length")))))
                        .then(Commands.literal("stop").executes(context -> {
                            ChartPlayback.unload(context.getSource().getLevel());
                            TrackMotion.stop(context.getSource().getLevel());return 1;
                        }))
                        .then(debugTree());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> debugTree(){
        return Commands.literal("debug")
                        .then(Commands.literal("nameplate").executes(context->NamePanel.generate(context.getSource())))
                        .then(Commands.literal("nameplate_data").then(Commands.argument("json",com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(context->NamePanel.command(context.getSource(),com.mojang.brigadier.arguments.StringArgumentType.getString(context,"json")))))
                        .then(Commands.literal("song").executes(context->SongPanel.generate(context.getSource())))
                        .then(Commands.literal("song_data").then(Commands.argument("json",com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(context->SongPanel.command(context.getSource(),com.mojang.brigadier.arguments.StringArgumentType.getString(context,"json")))))
                        .then(Commands.literal("head_data")
                                .then(Commands.argument("values",com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(context -> {
                                            var source=context.getSource();
                                            try {
                                                String[] values=com.mojang.brigadier.arguments.StringArgumentType.getString(context,"values").trim().split("\\s+");
                                                if(values.length!=6)throw new IllegalArgumentException();
                                                return TrackHeadDisplay.command(source,Integer.parseInt(values[0]),Integer.parseInt(values[1]),
                                                        Integer.parseInt(values[2]),Integer.parseInt(values[3]),Integer.parseInt(values[4]),
                                                        Integer.parseInt(values[5]));
                                            } catch(IllegalArgumentException invalid) {
                                                CommandFeedback.failure(source,Component.literal("用法：/chunithm debug head_data critical justice attack miss maxCombo score"));return 0;
                                            }
                                        })))
                        .then(Commands.literal("head").executes(context -> TrackHead.generate(context.getSource())))
                        .then(Commands.literal("ground_slide").executes(context -> GroundSlide.generate(context.getSource())))
                        .then(Commands.literal("air_sensors").executes(context -> AirSensors.generate(context.getSource())))
                        .then(Commands.literal("track_empty")
                                .executes(context -> generate(context.getSource(),24))
                                .then(Commands.argument("length",IntegerArgumentType.integer(2,1024))
                                        .executes(context -> generate(context.getSource(),
                                                IntegerArgumentType.getInteger(context,"length")))))
                        .then(Commands.literal("test")
                                .executes(context -> test(context.getSource(),24,120,MusicQueueData.DEFAULT_SPEED))
                                .then(Commands.argument("length", IntegerArgumentType.integer(2,1024))
                                        .executes(context -> test(context.getSource(),IntegerArgumentType.getInteger(context,"length"),120,MusicQueueData.DEFAULT_SPEED))
                                        .then(Commands.argument("bpm",DoubleArgumentType.doubleArg(30,300))
                                                .then(Commands.argument("speed",DoubleArgumentType.doubleArg(MusicQueueData.MIN_SPEED,MusicQueueData.MAX_SPEED))
                                                        .executes(context -> test(context.getSource(),
                                                                IntegerArgumentType.getInteger(context,"length"),
                                                                DoubleArgumentType.getDouble(context,"bpm"),
                                                                DoubleArgumentType.getDouble(context,"speed")))))));
    }

    private static int test(CommandSourceStack source, int length, double bpm, double speed) {
        if ((RhythmTiming.end(length)-RhythmTiming.SPAWN_CENTER)/speed*bpm/60*2+4 > 512) {
            CommandFeedback.failure(source,Component.literal("同时显示的测试实体会超过 512 个，请缩短轨道、提高速度或降低 BPM。"));
            return 0;
        }
        if (generate(source,length) == 0) return 0;
        TrackMotion.start(source.getLevel(),Placement.track(source),length,bpm,speed);
        return 1;
    }

    private static BlockState state(String name,Direction facing) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID, name))
                .defaultBlockState().setValue(TrackBlock.FACING, facing);
    }

    static int generate(CommandSourceStack source, int length) {
        var level = source.getLevel();
        var placement=Placement.track(source);
        BlockPos origin=placement.origin();
        // Check the entire footprint before changing anything: no partially generated tracks.
        for (int z = 0; z < length; z++) for (int x = 0; x < 9; x++) {
            BlockPos pos = placement.block(x,0,z);
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)) {
                CommandFeedback.failure(source,Component.literal("轨道超出世界边界或已加载区域，请缩短长度或先加载区域。"));
                return 0;
            }
        }
        var deck = state("track",placement.forward().getOpposite());
        var left = state("track_left",placement.forward().getOpposite());
        var right = state("track_right",placement.forward().getOpposite());
        TrackMotion.stop(level);
        ChartPlayback.unload(level);
        TrackMotion.clearArea(level,placement,length);
        for (int z = 0; z < length; z++) for (int x = 0; x < 9; x++) {
            var block = x == 0 ? left : x == 8 ? right : deck;
            level.setBlock(placement.block(x,0,z), block, Block.UPDATE_ALL);
        }
        TrackMotion.addJudgment(level,placement,length);
        PartConnections.register(level,PartConnections.Kind.TRACK,placement,length);
        CommandFeedback.generated(source,"轨道",length);
        return 9 * length;
    }
}
