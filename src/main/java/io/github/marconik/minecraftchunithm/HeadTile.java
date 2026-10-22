package io.github.marconik.minecraftchunithm;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public final class HeadTile extends HorizontalDirectionalBlock {
    public HeadTile(Properties settings){super(settings);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
}
