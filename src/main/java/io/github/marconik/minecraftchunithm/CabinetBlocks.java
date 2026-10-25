package io.github.marconik.minecraftchunithm;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Common obsidian-strength properties and protection against accidental cabinet removal. */
public final class CabinetBlocks {
    private CabinetBlocks(){}
    public static BlockBehaviour.Properties properties(ResourceKey<Block> key){
        return BlockBehaviour.Properties.of().setId(key).strength(50.0f,1200.0f).requiresCorrectToolForDrops();
    }
    static boolean cabinet(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(MinecraftChunithm.MOD_ID);}
    static boolean canRemove(BlockState state,ItemStack tool){return !cabinet(state)||tool.isCorrectToolForDrops(state);}
    static void initialize(){
        // Applies to creative too, to prevent a stray empty-hand click from
        // destroying a complete machine. Right-click control windows are unaffected.
        AttackBlockCallback.EVENT.register((player,level,hand,pos,face)->
            canRemove(level.getBlockState(pos),player.getMainHandItem())?InteractionResult.PASS:InteractionResult.FAIL);
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,blockEntity)->canRemove(state,player.getMainHandItem()));
    }
}
