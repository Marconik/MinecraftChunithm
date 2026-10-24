package io.github.marconik.minecraftchunithm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Position-aware facade tiles keep the rear opening real while retaining old block IDs. */
final class PanelTile extends HorizontalDirectionalBlock {
    static final IntegerProperty COLUMN=IntegerProperty.create("column",0,6);
    static final BooleanProperty TERMINAL=BooleanProperty.create("terminal");
    private final boolean player;
    PanelTile(Properties properties,boolean player){
        super(properties);this.player=player;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(COLUMN,0).setValue(TERMINAL,false));
    }
    static double width(boolean player){return HeadTerminal.sideWidth(player);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,COLUMN,TERMINAL);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return HeadTerminal.shape(state.getValue(TERMINAL)?HeadTerminal.opening(state.getValue(COLUMN),1,3.5,1.5,width(player),1):null,state.getValue(FACING));
    }
    static void refresh(net.minecraft.server.level.ServerLevel level,PartConnections.Part part){
        for(int y=0;y<3;y++)for(int x=0;x<7;x++){
            var pos=part.placement.block(x,y,0);var state=level.getBlockState(pos);
            var updated=state.setValue(COLUMN,x).setValue(TERMINAL,y==1);
            if(state!=updated)level.setBlock(pos,updated,Block.UPDATE_CLIENTS);
        }
    }
}
