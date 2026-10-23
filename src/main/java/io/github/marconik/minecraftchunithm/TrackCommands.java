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
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("chunithm")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("assemble").executes(c->PartAssembly.generate(c.getSource(),24))
                                .then(Commands.argument("length",IntegerArgumentType.integer(2,1024))
                                        .executes(c->PartAssembly.generate(c.getSource(),IntegerArgumentType.getInteger(c,"length")))))
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
                                                source.sendFailure(Component.literal("用法：/chunithm head_data critical justice attack miss maxCombo score（完成度由分数自动计算）"));return 0;
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
                        .then(Commands.literal("stop").executes(context -> {
                            ChartPlayback.unload(context.getSource().getLevel());
                            TrackMotion.stop(context.getSource().getLevel());
                            context.getSource().sendSuccess(() -> Component.literal("运动测试已停止，测试音符和灰色节拍线已清除。"), false);
                            return 1;
                        }))
                        .then(Commands.literal("test")
                                .executes(context -> test(context.getSource(),24,120,10))
                                .then(Commands.argument("length", IntegerArgumentType.integer(2,1024))
                                        .executes(context -> test(context.getSource(),IntegerArgumentType.getInteger(context,"length"),120,10))
                                        .then(Commands.argument("bpm",DoubleArgumentType.doubleArg(30,300))
                                                .then(Commands.argument("speed",DoubleArgumentType.doubleArg(0.25,20))
                                                        .executes(context -> test(context.getSource(),
                                                                IntegerArgumentType.getInteger(context,"length"),
                                                                DoubleArgumentType.getDouble(context,"bpm"),
                                                                DoubleArgumentType.getDouble(context,"speed")))))))
                        .then(Commands.literal("track")
                                .then(Commands.argument("length", IntegerArgumentType.integer(2, 1024))
                                        .executes(context -> generate(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "length")))))));
    }

    private static int test(CommandSourceStack source, int length, double bpm, double speed) {
        if ((RhythmTiming.end(length)-RhythmTiming.SPAWN_CENTER)/speed*bpm/60*2+4 > 512) {
            source.sendFailure(Component.literal("同时显示的测试实体会超过 512 个，请缩短轨道、提高速度或降低 BPM。"));
            return 0;
        }
        if (generate(source,length) == 0) return 0;
        TrackMotion.start(source.getLevel(),Placement.track(source),length,bpm,speed);
        source.sendSuccess(() -> Component.literal("开始随机 TAP 测试：BPM="+bpm+"，速度="+speed
                +" 格/秒，四分音符间隔="+(60/bpm)+" 秒。使用 /chunithm stop 停止。"),false);
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
                source.sendFailure(Component.literal("轨道超出世界边界或已加载区域，请缩短长度或先加载区域。"));
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
        source.sendSuccess(() -> Component.literal("已生成轨道（含尾部判定线）：有效宽 8 格 / 16 分区，总宽 9 格，总高 0.75 格，长 "
                + length + " 格。"), true);
        return 9 * length;
    }
}
