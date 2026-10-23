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
import net.minecraft.world.phys.Vec3;

/** Mirrored physical AIR housings; lamps are decorative and do not detect input. */
public final class AirSensors {
    public static final int BASE_WIDTH=5,DEPTH=3,BASE_HEIGHT=1,PANEL_HEIGHT=3,PORT_COUNT=5;
    public static final double BORDER=1.0/6;
    private static final Block[][][] BASES=new Block[2][BASE_WIDTH][DEPTH];
    private static final Block[][][] PANELS=new Block[2][PANEL_HEIGHT][DEPTH];
    private AirSensors(){}

    public static void initialize(){
        register("air_sensor_base",0); // Keep the former plain base ID readable in old saves.
        for(int side=0;side<2;side++)for(int x=0;x<BASE_WIDTH;x++)for(int z=0;z<DEPTH;z++)
            BASES[side][x][z]=register(baseName(side==0,x,z),0);
        for(int side=0;side<2;side++)for(int row=0;row<PANEL_HEIGHT;row++)for(int z=0;z<DEPTH;z++)
            PANELS[side][row][z]=register(tileName(side==0,row,z),z==1?4:0);
    }
    private static Block register(String name,int light){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,name));
        return Registry.register(BuiltInRegistries.BLOCK,key,new HeadTile(BlockBehaviour.Properties.of().setId(key)
                .strength(2.5f).sound(SoundType.METAL).noOcclusion().lightLevel(state->light)));
    }
    static String tileName(boolean left,int row,int z){return "air_sensor_"+(left?"left":"right")+"_"+row+"_"+z;}
    static String baseName(boolean left,int x,int z){return "air_sensor_base_"+(left?"left":"right")+"_"+x+"_"+z;}
    static boolean occupied(boolean left,int x,int y){return y==0||x==(left?0:BASE_WIDTH-1);}
    public static Placement target(Placement ground,boolean left){
        return new Placement(ground.block(left?-BASE_WIDTH:GroundSlide.WIDTH,0,0),ground.forward());
    }
    public static double portHeight(int port){
        if(port<0||port>=PORT_COUNT)throw new IllegalArgumentException("Invalid AIR port");
        return BASE_HEIGHT+.5+port*.5;
    }
    public static Vec3 portPoint(Placement ground,boolean left,int port){
        return ground.point(left?1-BASE_WIDTH:GroundSlide.WIDTH+BASE_WIDTH-1,portHeight(port),DEPTH/2.0);
    }
    static boolean preflight(CommandSourceStack source,Placement ground){
        var level=source.getLevel();
        for(boolean left:new boolean[]{true,false}){
            var placement=target(ground,left);
            for(int y=0;y<=PANEL_HEIGHT;y++)for(int z=0;z<DEPTH;z++)for(int x=0;x<BASE_WIDTH;x++){
                if(!occupied(left,x,y))continue;
                var pos=placement.block(x,y,z);
                if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)){
                    source.sendFailure(Component.literal("Air 感应器超出已加载区域或世界边界，未生成。"));return false;
                }
            }
        }
        return true;
    }
    public static int generate(CommandSourceStack source){return generate(source,GroundSlide.placement(source));}
    static int generate(CommandSourceStack source,Placement ground){
        if(!preflight(source,ground))return 0;
        var level=source.getLevel();
        for(boolean left:new boolean[]{true,false}){
            var placement=target(ground,left);
            for(int y=0;y<=PANEL_HEIGHT;y++)for(int z=0;z<DEPTH;z++)for(int x=0;x<BASE_WIDTH;x++){
                if(!occupied(left,x,y))continue;
                var block=y==0?BASES[left?0:1][x][z]:PANELS[left?0:1][y-1][z];
                level.setBlock(placement.block(x,y,z),block.defaultBlockState().setValue(HeadTile.FACING,placement.forward()),Block.UPDATE_ALL);
            }
            PartConnections.register(level,left?PartConnections.Kind.AIR_LEFT:PartConnections.Kind.AIR_RIGHT,placement,1);
        }
        source.sendSuccess(()->Component.literal("已生成左右 Air 感应器：黑色磨制石材感表面、1/6 格白边、向外三箭头，5 个方孔左绿右红（装饰模型）。"),false);
        return 1;
    }
}
