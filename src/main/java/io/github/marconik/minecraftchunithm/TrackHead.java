package io.github.marconik.minecraftchunithm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Independent 11 x 4 x 1 controller facade, including a one-block pedestal. */
public final class TrackHead {
    private static final Block[][] TILES=new Block[4][11];
    private TrackHead() {}
    public static void initialize() {
        for(int row=0;row<4;row++)for(int col=0;col<11;col++) {
            var id=Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"head_"+col+"_"+row);
            var key=ResourceKey.create(Registries.BLOCK,id);
            TILES[row][col]=Registry.register(BuiltInRegistries.BLOCK,key,new HeadTile(
                    BlockBehaviour.Properties.of().setId(key).strength(2).sound(SoundType.METAL)));
        }
    }
    public static int generate(CommandSourceStack source) {
        var level=source.getLevel();
        // Preserve the previous nine-block centre: extend one block on each side.
        var placement=Placement.head(source);
        var origin=placement.origin();
        for(int row=0;row<4;row++)for(int col=0;col<11;col++) {
            var pos=placement.block(col,row,0);
            if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)) {
                source.sendFailure(Component.literal("头部超出已加载区域或世界边界。"));return 0;
            }
        }
        for(int row=0;row<4;row++)for(int col=0;col<11;col++)
            level.setBlock(placement.block(col,row,0),TILES[row][col].defaultBlockState().setValue(HeadTile.FACING,placement.forward()),Block.UPDATE_ALL);
        source.sendSuccess(()->Component.literal("已生成独立轨道头部：宽11、高4（含1格底座）、厚1，进度条宽8。正面朝向玩家。"),false);
        TrackHeadDisplay.update(level,placement,new HeadDisplayData(0,0,0,0,0,0));
        return 44;
    }
}
