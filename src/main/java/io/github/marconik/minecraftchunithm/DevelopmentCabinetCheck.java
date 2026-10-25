package io.github.marconik.minecraftchunithm;

import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;

/** Actual tag/tool/explosion/removal checks, invoked only in the isolated opt-in test world. */
final class DevelopmentCabinetCheck {
    static void run(ServerLevel level){
        var player=level.getServer().getPlayerList().getPlayers().getFirst();
        var diamond=new ItemStack(Items.DIAMOND_PICKAXE);var netherite=new ItemStack(Items.NETHERITE_PICKAXE);
        int count=0;
        for(var block:BuiltInRegistries.BLOCK)if(BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(MinecraftChunithm.MOD_ID)){
            var state=block.defaultBlockState();
            if(state.getDestroySpeed(level,BlockPos.ZERO)!=50||block.getExplosionResistance()!=1200||state.ignitedByLava())
                throw new AssertionError("Cabinet material differs from obsidian: "+BuiltInRegistries.BLOCK.getKey(block));
            if(!state.requiresCorrectToolForDrops()||!state.is(BlockTags.MINEABLE_WITH_PICKAXE)||!state.is(BlockTags.NEEDS_DIAMOND_TOOL))
                throw new AssertionError("Missing cabinet mining tags: "+BuiltInRegistries.BLOCK.getKey(block));
            for(var tool:new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.WOODEN_PICKAXE),new ItemStack(Items.STONE_PICKAXE),
                    new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.GOLDEN_PICKAXE),new ItemStack(Items.DIAMOND_AXE)})
                if(CabinetBlocks.canRemove(state,tool))throw new AssertionError("Incorrect tool can remove a cabinet block");
            if(!CabinetBlocks.canRemove(state,diamond)||!CabinetBlocks.canRemove(state,netherite))throw new AssertionError("Diamond/netherite pick cannot remove a cabinet block");
            count++;
        }
        if(count!=154)throw new AssertionError("Incomplete cabinet coverage: "+count);
        var position=new BlockPos(4240,80,4096);level.getChunk(position);
        var block=BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"track"));
        var oldState=level.getBlockState(position);var oldHand=player.getMainHandItem().copy();
        try{
            level.setBlockAndUpdate(position,block.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            if(player.gameMode.destroyBlock(position)||level.getBlockState(position).getBlock()!=block)throw new AssertionError("Empty-hand creative removal was not blocked");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            if(player.gameMode.destroyBlock(position)||level.getBlockState(position).getBlock()!=block)throw new AssertionError("Iron pick removal was not blocked");
            level.explode(null,position.getX()+.5,position.getY()+1.5,position.getZ()+.5,4.0f,Level.ExplosionInteraction.TNT);
            if(level.getBlockState(position).getBlock()!=block)throw new AssertionError("TNT destroyed the cabinet block");
            player.setItemInHand(InteractionHand.MAIN_HAND,diamond);
            if(!player.gameMode.destroyBlock(position)||!level.getBlockState(position).isAir())throw new AssertionError("Diamond pick could not clear the cabinet block");
        }finally{player.setItemInHand(InteractionHand.MAIN_HAND,oldHand);level.setBlockAndUpdate(position,oldState);}
        try{Files.writeString(FabricLoader.getInstance().getGameDir().resolve("cabinet-protection-checks.json"),
            "{\"blocks\":"+count+",\"hardness\":50,\"blastResistance\":1200,\"diamondAndNetherite\":true,\"incorrectToolsBlocked\":true,\"tntSurvived\":true}");}
        catch(java.io.IOException error){throw new RuntimeException(error);}
        MinecraftChunithm.LOGGER.info("CABINET PROTECTION SMOKE TEST PASSED: {} blocks, obsidian hardness/resistance, nonflammability, correct tool tags, creative hand/iron rejection, real TNT blast and diamond removal.",count);
    }
}
