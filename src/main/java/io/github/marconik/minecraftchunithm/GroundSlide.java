package io.github.marconik.minecraftchunithm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Physical 13 x 3 cabinet with an exact 8 x 2 touch surface and decorative coin slot. */
public final class GroundSlide {
    public static final int WIDTH=13,DEPTH=3,LIGHT_WIDTH=8,LIGHT_DEPTH=2,LANES=16;
    public static final int TRACK_SIDE_EXTENSION=(WIDTH-9)/2;
    public static final double HEIGHT=1,SURFACE_HEIGHT=.75;
    /** Tile-local aperture bounds in model pixels, shared with the collision geometry. */
    record Recess(double minX,double minZ,double maxX,double maxZ){}
    static Recess recess(int x,int z){
        double side=(WIDTH-LIGHT_WIDTH)/2.0,end=(DEPTH-LIGHT_DEPTH)/2.0;
        double minX=Math.max(0,(side-x)*16),maxX=Math.min(16,(side+LIGHT_WIDTH-x)*16);
        double minZ=Math.max(0,(end-z)*16),maxZ=Math.min(16,(end+LIGHT_DEPTH-z)*16);
        return minX<maxX&&minZ<maxZ?new Recess(minX,minZ,maxX,maxZ):null;
    }
    private static final Block[][] TILES=new Block[DEPTH][WIDTH];
    private GroundSlide(){}

    public static void initialize(){
        for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++){
            var id=Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"ground_slide_"+x+"_"+z);
            var key=ResourceKey.create(Registries.BLOCK,id);
            TILES[z][x]=Registry.register(BuiltInRegistries.BLOCK,key,new GroundSlideBlock(
                    CabinetBlocks.properties(key).sound(SoundType.METAL)
                            .lightLevel(state->12).noOcclusion(),x,z));
        }
        GroundSlidePressure.initialize();
    }

    static boolean matchesTile(net.minecraft.world.level.block.state.BlockState state,int x,int z,net.minecraft.core.Direction forward){
        return state.is(TILES[z][x])&&state.getValue(GroundSlideBlock.FACING)==forward;
    }

    /** The player's standing block is the centre tile of the three-block-deep cabinet. */
    public static Placement placement(CommandSourceStack source){
        var forward=Placement.looking(source);
        return new Placement(Placement.standing(source).relative(forward.getCounterClockWise(),-WIDTH/2).relative(forward,-1),forward);
    }

    public static int generate(CommandSourceStack source){return generate(source,placement(source));}

    static int generate(CommandSourceStack source,Placement placement){
        var level=source.getLevel();
        for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++){
            var pos=placement.block(x,0,z);
            if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)){
                CommandFeedback.failure(source,Component.literal("Ground Slide 超出已加载区域或世界边界，未生成。"));
                return 0;
            }
        }
        for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++)
            level.setBlock(placement.block(x,0,z),TILES[z][x].defaultBlockState()
                    .setValue(GroundSlideBlock.FACING,placement.forward()),Block.UPDATE_ALL);
        PartConnections.register(level,PartConnections.Kind.GROUND_SLIDE,placement,1);
        CommandFeedback.generated(source,"Ground Slide",null);
        return WIDTH*DEPTH;
    }
}
