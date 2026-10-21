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
                        .then(Commands.literal("stop").executes(context -> {
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
        TrackMotion.start(source.getLevel(),BlockPos.containing(source.getPosition()).below(),length,bpm,speed);
        source.sendSuccess(() -> Component.literal("开始随机 TAP 测试：BPM="+bpm+"，速度="+speed
                +" 格/秒，四分音符间隔="+(60/bpm)+" 秒。使用 /chunithm stop 停止。"),false);
        return 1;
    }

    private static BlockState state(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID, name))
                .defaultBlockState().setValue(TrackBlock.FACING, Direction.NORTH);
    }

    private static int generate(CommandSourceStack source, int length) {
        var level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition()).below();
        // Check the entire footprint before changing anything: no partially generated tracks.
        for (int z = 0; z < length; z++) for (int x = 0; x < 9; x++) {
            BlockPos pos = origin.offset(x, 0, z);
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)) {
                source.sendFailure(Component.literal("轨道超出世界边界或已加载区域，请缩短长度或先加载区域。"));
                return 0;
            }
        }
        var deck = state("track");
        var left = state("track_left");
        var right = state("track_right");
        TrackMotion.stop(level);
        TrackMotion.clearArea(level,origin,length);
        for (int z = 0; z < length; z++) for (int x = 0; x < 9; x++) {
            var block = x == 0 ? left : x == 8 ? right : deck;
            level.setBlock(origin.offset(x, 0, z), block, Block.UPDATE_ALL);
        }
        TrackMotion.addJudgment(level,origin,length);
        source.sendSuccess(() -> Component.literal("已生成轨道：有效宽 8 格 / 16 分区，总宽 9 格，总高 0.75 格，长 "
                + length + " 格。"), true);
        return 9 * length;
    }
}
