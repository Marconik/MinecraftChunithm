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

public final class NamePanel {
    private static final String TAG="minecraftchunithm.name_display";
    private static Block tile;
    private NamePanel(){}
    public static void initialize(){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"name_panel"));
        tile=Registry.register(BuiltInRegistries.BLOCK,key,new HeadTile(BlockBehaviour.Properties.of().setId(key).strength(2)));
    }
    public static Placement placement(CommandSourceStack source){
        return placement(net.minecraft.core.BlockPos.containing(source.getPosition()),Placement.looking(source));
    }
    public static Placement placement(net.minecraft.core.BlockPos playerBlock,net.minecraft.core.Direction looking){
        var front=looking.getOpposite();
        // Mirror the song panel's lower-left anchor: the block ahead is lower-right.
        var right=playerBlock.relative(looking);
        return new Placement(right.relative(front.getCounterClockWise(),-6),front);
    }
    public static int generate(CommandSourceStack source){
        var p=placement(source);var level=source.getLevel();
        for(int y=0;y<3;y++)for(int x=0;x<7;x++){
            var pos=p.block(x,y,0);
            if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)){
                source.sendFailure(Component.literal("名牌面板超出已加载区域或世界边界。"));return 0;
            }
        }
        for(int y=0;y<3;y++)for(int x=0;x<7;x++)level.setBlock(p.block(x,y,0),tile.defaultBlockState().setValue(HeadTile.FACING,p.forward()),Block.UPDATE_ALL);
        PartConnections.register(level,PartConnections.Kind.PLAYER,p,1);
        update(level,p,NameDisplayData.defaults());
        source.sendSuccess(()->Component.literal("已生成名牌面板：右下角在玩家所在方格前一格，向左展开，面朝玩家；宽7、高3、厚1。"),false);
        return 21;
    }
    private static void add(ServerLevel level,Placement p,Display e,double x,double y,double z,Vector3f scale){
        var pos=p.point(x,y,z);e.setPos(pos.x,pos.y,pos.z);e.setYRot(p.yaw());
        e.setTransformation(new Transformation(null,null,scale,null));e.setBrightnessOverride(new Brightness(15,15));
        e.setWidth(8);e.setHeight(4);e.addTag(TAG);level.addFreshEntity(e);
    }
    private static void image(ServerLevel level,Placement p,double x,double y,double w,double h,double z,Identifier model){
        var e=EntityTypes.ITEM_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);if(e==null)throw new IllegalStateException();
        var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft","paper")));
        stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,model);
        e.setItemStack(stack);e.setItemTransform(ItemDisplayContext.FIXED);
        add(level,p,e,x,y,z,new Vector3f((float)w,(float)h,.01f));e.setYRot(p.yaw()+180);
    }
    private static Identifier art(String name){
        return Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"name_panel/"+name);
    }
    private static void text(ServerLevel level,Placement p,double x,double y,String value,float desired,double maxWidth,int color,boolean bold,boolean left){
        var e=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);if(e==null)throw new IllegalStateException();
        double units=NameplateTypography.width(value,bold);
        float scale=(float)Math.min(desired,maxWidth/Math.max(.025,units*.025));
        double center=left?x+units*.025*scale/2-.025*scale:x;
        e.setText(Component.literal(value).withStyle(s->s.withColor(color).withBold(bold)
                .withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft","uniform")))));
        e.setBackgroundColor(0);e.setLineWidth(4096);e.setFlags((byte)0);
        // Centre the uniform font's eight-pixel ink height, not the line box.
        add(level,p,e,center,y-scale*.1375,1.09,new Vector3f(scale));
    }
    public static void update(ServerLevel level,Placement p,NameDisplayData data){
        PartConnections.playerUpdated(level,p,data);
        for(var e:level.getEntitiesOfClass(Display.class,new AABB(p.point(-.1,0,.9),p.point(7.1,3.1,1.3)),e->e.entityTags().contains(TAG)))e.discard();
        image(level,p,3.5,1.5,7,3,1.025,art("background"));
        text(level,p,.70,2.57,"TEAM",.78f,.76,0x25252c,true,false);
        text(level,p,1.25,2.57,data.team(),.92f,5.35,0xffffff,false,true);
        boolean gold=data.titleColor()==NameDisplayData.TitleColor.GOLD;
        image(level,p,3.5,2.00,6.50,.5625,1.045,art(gold?"gold":"silver"));
        text(level,p,3.5,2.00,data.title(),.98f,6.10,0x24242b,true,false);
        text(level,p,.32,1.17,"Lv. "+data.level(),1.00f,1.05,0x18181c,false,true);
        text(level,p,1.42,1.17,data.name(),1.55f,3.68,0x141418,false,true);
        text(level,p,.32,.49,"RATING",.74f,1.02,0x34343b,false,true);
        text(level,p,1.42,.49,data.rating(),.95f,3.68,0x34343b,false,true);
        image(level,p,6.00,.797,1.18,1.18,1.055,data.characterModel());
        DisplayFinish.polish(level,p,TAG,5.41,.207,1.18,1.18,1.058,false,false);
    }
    public static int command(CommandSourceStack source,String json){
        try{
            var o=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            var data=new NameDisplayData(o.get("team").getAsString(),o.get("title").getAsString(),
                    NameDisplayData.TitleColor.valueOf(o.get("titleColor").getAsString().toUpperCase(java.util.Locale.ROOT)),
                    o.get("level").getAsString(),o.get("name").getAsString(),Identifier.parse(o.get("character").getAsString()),o.get("rating").getAsString());
            var p=placement(source);
            for(int y=0;y<3;y++)for(int x=0;x<7;x++)if(source.getLevel().getBlockState(p.block(x,y,0)).getBlock()!=tile){
                source.sendFailure(Component.literal("请先在相同位置和朝向执行 /chunithm nameplate。"));return 0;
            }
            update(source.getLevel(),p,data);source.sendSuccess(()->Component.literal("名牌信息已更新。"),false);return 1;
        }catch(RuntimeException invalid){source.sendFailure(Component.literal("名牌数据无效，请检查 JSON 字段、SILVER/GOLD 称号颜色和角色模型资源 ID。"));return 0;}
    }
}
