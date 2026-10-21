package io.github.marconik.minecraftchunithm;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A 10/16-high deck; edge blocks reserve their outer half for a 12/16-high rail. */
public final class TrackBlock extends HorizontalDirectionalBlock {
    public enum Part { DECK, LEFT, RIGHT, JUDGMENT }
    private final Part part;
    public static final double SURFACE_HEIGHT = 0.625;
    private static final VoxelShape DECK = Block.box(0, 0, 0, 16, 10, 16);

    public TrackBlock(Properties properties, Part part) {
        super(properties);
        this.part = part;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Part part() { return part; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (part != Part.LEFT && part != Part.RIGHT) return DECK;
        Direction outer = part == Part.LEFT ? state.getValue(FACING).getCounterClockWise()
                : state.getValue(FACING).getClockWise();
        VoxelShape rail = switch (outer) {
            case WEST -> Block.box(0, 10, 0, 8, 12, 16);
            case EAST -> Block.box(8, 10, 0, 16, 12, 16);
            case NORTH -> Block.box(0, 10, 0, 16, 12, 8);
            case SOUTH -> Block.box(0, 10, 8, 16, 12, 16);
            default -> throw new IllegalStateException("Track must face horizontally");
        };
        return Shapes.or(DECK, rail);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
