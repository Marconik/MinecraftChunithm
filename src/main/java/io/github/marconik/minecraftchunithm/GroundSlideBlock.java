package io.github.marconik.minecraftchunithm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.EnumMap;

/** Full-block cabinet rim with a three-quarter-block recessed touch surface. */
final class GroundSlideBlock extends HorizontalDirectionalBlock {
    // Visual only: bit 0/1 addresses the two local half-blocks, never redstone power.
    static final IntegerProperty PRESSED=IntegerProperty.create("pressed",0,3);
    private final EnumMap<Direction,VoxelShape> shapes=new EnumMap<>(Direction.class);

    GroundSlideBlock(Properties properties,int x,int z) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PRESSED,0));
        var opening=GroundSlide.recess(x,z);
        for(var direction:Direction.Plane.HORIZONTAL){
            VoxelShape shape=Block.box(0,0,0,16,GroundSlide.SURFACE_HEIGHT*16,16);
            if(opening==null)shape=Shapes.block();
            else {
                shape=Shapes.or(shape,box(direction,0,0,opening.minX(),16));
                shape=Shapes.or(shape,box(direction,opening.maxX(),0,16,16));
                shape=Shapes.or(shape,box(direction,opening.minX(),0,opening.maxX(),opening.minZ()));
                shape=Shapes.or(shape,box(direction,opening.minX(),opening.maxZ(),opening.maxX(),16));
            }
            shapes.put(direction,shape);
        }
    }

    private static VoxelShape box(Direction forward,double x0,double z0,double x1,double z1){
        if(x0>=x1||z0>=z1)return Shapes.empty();
        var across=forward.getCounterClockWise();
        double ax=8+across.getStepX()*(x0-8)+forward.getStepX()*(z0-8);
        double az=8+across.getStepZ()*(x0-8)+forward.getStepZ()*(z0-8);
        double bx=8+across.getStepX()*(x1-8)+forward.getStepX()*(z1-8);
        double bz=8+across.getStepZ()*(x1-8)+forward.getStepZ()*(z1-8);
        return Block.box(Math.min(ax,bx),GroundSlide.SURFACE_HEIGHT*16,Math.min(az,bz),
                Math.max(ax,bx),GroundSlide.HEIGHT*16,Math.max(az,bz));
    }

    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapes.get(state.getValue(FACING));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PRESSED);}
}
