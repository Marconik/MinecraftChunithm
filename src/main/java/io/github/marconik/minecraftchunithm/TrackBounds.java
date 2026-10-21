package io.github.marconik.minecraftchunithm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Reconstruct a complete nine-block row; never infer bounds from arbitrary floor blocks. */
public record TrackBounds(double min, double max, boolean alongZ) {
    public static TrackBounds find(Level level, BlockPos clicked) {
        var state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TrackBlock)) return null;
        Direction facing = state.getValue(TrackBlock.FACING);
        Direction across = facing.getClockWise();
        for (int offset = 0; offset < 9; offset++) {
            BlockPos left = clicked.relative(across.getOpposite(), offset);
            var candidate = level.getBlockState(left);
            if (!(candidate.getBlock() instanceof TrackBlock block) || block.part() != TrackBlock.Part.LEFT
                    || candidate.getValue(TrackBlock.FACING) != facing) continue;
            for (int i = 0; i < 9; i++) {
                var current = level.getBlockState(left.relative(across, i));
                if (!(current.getBlock() instanceof TrackBlock tile)
                        || current.getValue(TrackBlock.FACING) != facing) return null;
                if (i == 8 && tile.part() != TrackBlock.Part.RIGHT) return null;
                if (i > 0 && i < 8 && tile.part() != TrackBlock.Part.DECK
                        && tile.part() != TrackBlock.Part.JUDGMENT) return null;
            }
            BlockPos right = left.relative(across, 8);
            boolean alongZ = across.getAxis() == Direction.Axis.Z;
            double first = alongZ ? left.getZ() : left.getX();
            double last = alongZ ? right.getZ() : right.getX();
            return new TrackBounds(Math.min(first, last) + 0.5, Math.max(first, last) + 0.5, alongZ);
        }
        return null;
    }

    public boolean contains(double start, double width) {
        return start >= min - 1e-7 && start + width <= max + 1e-7;
    }
}
