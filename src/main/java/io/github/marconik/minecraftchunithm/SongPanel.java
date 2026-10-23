package io.github.marconik.minecraftchunithm;

import com.mojang.math.Transformation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public final class SongPanel {
    private static final String TAG="minecraftchunithm.song_display";
    private static Block tile;
    private SongPanel(){}
    public static void initialize(){
        var key=ResourceKey.create(Registries.BLOCK,id("song_panel"));
        tile=Registry.register(BuiltInRegistries.BLOCK,key,new HeadTile(BlockBehaviour.Properties.of().setId(key).strength(2)));
    }
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,path);}
    public static Placement placement(CommandSourceStack source){
        var looking=Placement.looking(source);
        var lowerLeft=net.minecraft.core.BlockPos.containing(source.getPosition()).relative(looking);
        return new Placement(lowerLeft,looking.getOpposite());
    }
    public static int generate(CommandSourceStack source){
        var p=placement(source);var level=source.getLevel();
        for(int y=0;y<3;y++)for(int x=0;x<7;x++){
            var pos=p.block(x,y,0);
            if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)){
                source.sendFailure(Component.literal("曲目面板超出已加载区域或世界边界。"));return 0;
            }
        }
        for(int y=0;y<3;y++)for(int x=0;x<7;x++)level.setBlock(p.block(x,y,0),tile.defaultBlockState().setValue(HeadTile.FACING,p.forward()),Block.UPDATE_ALL);
        PartConnections.register(level,PartConnections.Kind.SONG,p,1);
        update(level,p,SongDisplayData.defaults());
        source.sendSuccess(()->Component.literal("已生成曲目面板：左下角在玩家所在方格前一格，正面朝向玩家，宽7、高3、厚1。"),false);return 21;
    }
    private static void add(ServerLevel level,Placement p,Display e,double x,double y,double z,Vector3f scale){
        var pos=p.point(x,y,z);e.setPos(pos.x,pos.y,pos.z);e.setYRot(p.yaw());
        e.setTransformation(new Transformation(null,null,scale,null));e.setBrightnessOverride(new Brightness(15,15));
        e.setWidth(8);e.setHeight(4);e.addTag(TAG);level.addFreshEntity(e);
    }
    private static void rect(ServerLevel level,Placement p,double x,double y,double w,double h,String material,double z){
        var e=EntityTypes.BLOCK_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        if(e==null)throw new IllegalStateException();
        e.setBlockState(BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft",material)).defaultBlockState());
        add(level,p,e,x,y,z,new Vector3f((float)w,(float)h,.01f));
    }
    private static void text(ServerLevel level,Placement p,double x,double y,String text,float desired,double maxWidth,int color,boolean bold){
        var e=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);if(e==null)throw new IllegalStateException();
        double units=textWidthBound(text,bold);
        float scale=(float)Math.min(desired,maxWidth/Math.max(.025,units*.025));
        e.setText(Component.literal(text).withStyle(s->s.withColor(color).withBold(bold)));
        e.setBackgroundColor(0);e.setLineWidth(4096);e.setFlags((byte)0);
        add(level,p,e,x,y-scale*.1125,1.075,new Vector3f(scale));
    }
    private static void textLeft(ServerLevel level,Placement p,double x,double y,String value,float desired,double maxWidth,int color,boolean bold){
        double units=textWidthBound(value,bold);
        float scale=(float)Math.min(desired,maxWidth/Math.max(.025,units*.025));
        double width=units*.025*scale;
        // TextDisplay adds one pixel before the centered line. Include bold's
        // extra advance so the left edge cannot spill into the cover.
        text(level,p,x+width/2-.025*scale,y,value,desired,maxWidth,color,bold);
    }
    private static double textWidthBound(String value,boolean bold){
        // Conservative default-font advance; narrow glyphs leave extra inset.
        return value.codePoints().map(c->(c>255?9:6)+(bold?1:0)).sum();
    }
    public static void update(ServerLevel level,Placement p,SongDisplayData data){
        PartConnections.songUpdated(level,p,data);
        for(var e:level.getEntitiesOfClass(Display.class,new AABB(p.point(-.1,0,.9),p.point(7.1,3.1,1.3)),e->e.entityTags().contains(TAG)))e.discard();
        String theme=switch(data.difficulty()){case MASTER->"purple_concrete";case EXPERT->"red_concrete";case ULTIMA->"black_concrete";};
        rect(level,p,0,0,7,3,theme,1.001);
        rect(level,p,.06,2.5,6.88,.44,"black_concrete",1.014);
        rect(level,p,.06,.06,6.88,1.0,"white_concrete",1.014);
        rect(level,p,3.49,2.58,.018,.28,"gray_concrete",1.028);
        rect(level,p,5.62,1.1,1.29,1.34,"white_concrete",1.014);
        rect(level,p,5.66,1.14,1.21,.90,"black_concrete",1.028);
        text(level,p,1.85,2.73,String.format(java.util.Locale.ROOT,"SPEED: %.2f",data.speed()),.75f,3.2,0xffffff,true);
        text(level,p,5.25,2.73,"MIRROR: "+(data.mirror()?"ON":"OFF"),.75f,3,0xffffff,true);
        textLeft(level,p,2.48,2.26,"TRACK "+data.track(),.95f,3.0,0xffffff,true);
        textLeft(level,p,2.50,1.65,data.difficulty().name(),2.55f,3.05,0xffffff,true);
        text(level,p,6.265,2.25,"LEVEL",.70f,1.17,0x10242b,true);
        text(level,p,6.265,1.58,data.level(),2.25f,1.16,0xffffff,true);
        textLeft(level,p,2.48,.77,data.title(),1.25f,4.28,0x15151b,false);
        rect(level,p,2.47,.48,4.28,.018,"purple_concrete",1.030);
        textLeft(level,p,2.48,.27,data.artist(),.72f,4.28,0x30303a,false);
        rect(level,p,.12,.12,2.28,2.28,"white_concrete",1.028);
        var cover=EntityTypes.ITEM_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);if(cover==null)throw new IllegalStateException();
        var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft","paper")));
        stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,data.coverModel());
        cover.setItemStack(stack);cover.setItemTransform(ItemDisplayContext.FIXED);
        add(level,p,cover,1.26,1.26,1.06,new Vector3f(2.20f,2.20f,1));
        // Generated item sprites face the opposite direction to text displays.
        // A half turn shows the readable front rather than its mirrored rear.
        cover.setYRot(p.yaw()+180);
        // Generated sprites protrude 1/32 block from their centre plane.
        DisplayFinish.polish(level,p,TAG,.16,.16,2.20,2.20,1.095,false,false);
    }
    public static int command(CommandSourceStack source,String json){
        try{
            var o=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            var data=new SongDisplayData(Identifier.parse(o.get("cover").getAsString()),o.get("title").getAsString(),
                    o.get("artist").getAsString(),o.get("track").getAsInt(),SongDisplayData.Difficulty.valueOf(o.get("difficulty").getAsString().toUpperCase(java.util.Locale.ROOT)),
                    o.get("level").getAsString(),o.get("speed").getAsDouble(),o.get("mirror").getAsBoolean());
            var p=placement(source);
            for(int y=0;y<3;y++)for(int x=0;x<7;x++)if(source.getLevel().getBlockState(p.block(x,y,0)).getBlock()!=tile){
                source.sendFailure(Component.literal("请先在相同位置和朝向执行 /chunithm song。"));return 0;
            }
            update(source.getLevel(),p,data);source.sendSuccess(()->Component.literal("曲目信息已更新。"),false);return 1;
        }catch(RuntimeException invalid){source.sendFailure(Component.literal("曲目数据无效，请检查 JSON 字段、难度和封面模型资源 ID。"));return 0;}
    }
}
