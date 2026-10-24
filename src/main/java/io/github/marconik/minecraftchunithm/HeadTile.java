package io.github.marconik.minecraftchunithm;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class HeadTile extends HorizontalDirectionalBlock {
    private java.util.Map<Direction,net.minecraft.world.phys.shapes.VoxelShape> terminalShapes;
    public HeadTile(Properties settings){super(settings);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));}
    HeadTile(Properties settings,int col,int row){
        this(settings);
        if(HeadTerminal.opening(col,row)!=null){
            terminalShapes=new java.util.EnumMap<>(Direction.class);
            for(var direction:Direction.Plane.HORIZONTAL)terminalShapes.put(direction,HeadTerminal.shape(col,row,direction));
        }
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
        return terminalShapes==null?net.minecraft.world.phys.shapes.Shapes.block():terminalShapes.get(state.getValue(FACING));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
}
