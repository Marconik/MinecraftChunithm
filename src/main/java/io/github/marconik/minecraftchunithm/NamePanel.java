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
    private static final java.util.Map<ServerLevel,java.util.Set<PartConnections.Part>> UPDATED=new java.util.WeakHashMap<>();
    private NamePanel(){}
    public static void initialize(){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"name_panel"));
        tile=Registry.register(BuiltInRegistries.BLOCK,key,new PanelTile(CabinetBlocks.properties(key).noOcclusion(),true));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(level->{
            if(level.getGameTime()%20!=0)return;
            var refreshed=UPDATED.computeIfAbsent(level,l->java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
            for(var part:PartConnections.ofKind(level,PartConnections.Kind.PLAYER))
                if(!refreshed.contains(part)&&PartConnections.integrity(level,part)==PartConnections.Status.CONNECTED){
                    update(level,part.placement,part.player);refreshed.add(part);
                }
        });
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
                CommandFeedback.failure(source,Component.literal("名牌面板超出已加载区域或世界边界。"));return 0;
            }
        }
        for(int y=0;y<3;y++)for(int x=0;x<7;x++)level.setBlock(p.block(x,y,0),tile.defaultBlockState().setValue(HeadTile.FACING,p.forward()),Block.UPDATE_ALL);
        PartConnections.register(level,PartConnections.Kind.PLAYER,p,1);
        update(level,p,NameDisplayData.defaults());
        CommandFeedback.generated(source,"玩家信息面板",null);
        return 21;
    }
    private static void add(ServerLevel level,Placement p,Display e,double x,double y,double z,Vector3f scale){
        var pos=p.point(x,y,FacadeSurface.depth(z));e.setPos(pos.x,pos.y,pos.z);e.setYRot(p.yaw());
        e.setTransformation(new Transformation(null,null,new Vector3f(scale).mul(1,1,FacadeSurface.DEPTH_SCALE),null));e.setBrightnessOverride(new Brightness(15,15));
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
        double units=NameplateTypography.width(value,bold);
        float scale=(float)Math.min(desired,maxWidth/Math.max(.025,units*.025));
        textLayer(level,p,x,y,value,scale,scale,color,bold,left,1.09);
    }
    private static Display.TextDisplay textLayer(ServerLevel level,Placement p,double x,double y,String value,float scaleX,float scaleY,int color,boolean bold,boolean left,double depth){
        var e=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);if(e==null)throw new IllegalStateException();
        double center=left?x+NameplateTypography.width(value,bold)*.025*scaleX/2-.025*scaleX:x;
        e.setText(Component.literal(value).withStyle(s->s.withColor(color).withBold(bold)
                .withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft","uniform")))));
        e.setBackgroundColor(0);e.setLineWidth(4096);e.setFlags((byte)0);
        // Centre the uniform font's eight-pixel ink height, not the line box.
        add(level,p,e,center,y-scaleY*.1375,depth,new Vector3f(scaleX,scaleY,1));
        return e;
    }
    private static void spacedText(ServerLevel level,Placement p,double x,double centerY,String value,float size,double maxWidth,int color,boolean left){
        var glyphs=value.codePoints().mapToObj(c->new String(Character.toChars(c))).toList();
        double spacing=1.5;
        double units=glyphs.stream().mapToDouble(glyph->NameplateTypography.width(glyph,true)).sum()
                +Math.max(0,glyphs.size()-1)*spacing;
        float fitted=(float)Math.min(size,maxWidth/Math.max(.025,units*.025));
        double cursor=left?x:x-units*.025*fitted/2;
        for(var glyph:glyphs){
            textLayer(level,p,cursor,centerY+.025*size,glyph,fitted,size,color,true,true,1.09);
            cursor+=(NameplateTypography.width(glyph,true)+spacing)*.025*fitted;
        }
    }
    /** Preserve row height for long names; only their horizontal extent is fitted. */
    private static void rowText(ServerLevel level,Placement p,double x,double centerY,String value,float size,double width,int color,boolean outline){
        // Uniform's Latin capitals occupy 11 native pixels (5.5 logical
        // pixels); their ink centre sits one logical pixel below the line centre.
        centerY+=.025*size;
        float fitted=(float)Math.min(size,width/Math.max(.025,NameplateTypography.width(value,true)*.025));
        if(outline){
            double border=.025*size*.65;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)
                textLayer(level,p,x+dx*border,centerY+dy*border,value,fitted,size,0x000000,true,true,1.085).setBrightnessOverride(null);
        }
        var ink=textLayer(level,p,x,centerY,value,fitted,size,color,true,true,1.09);
        // The rating keeps its crisp black outline but follows world lighting.
        if(outline)ink.setBrightnessOverride(null);
    }
    public static void update(ServerLevel level,Placement p,NameDisplayData data){
        PartConnections.playerUpdated(level,p,data);
        for(var e:level.getEntitiesOfClass(Display.class,new AABB(p.point(-.1,0,.9),p.point(7.1,3.1,1.3)),e->e.entityTags().contains(TAG)))e.discard();
        image(level,p,3.5,1.5,7,3,1.025,art("background"));
        // The supplied component already includes its TEAM label and crest.
        spacedText(level,p,1.48,2.585,data.team(),.76f,5.10,0xffffff,true);
        boolean gold=data.titleColor()==NameDisplayData.TitleColor.GOLD;
        image(level,p,3.5,2.00,6.50,.5625,1.045,art(gold?"gold":"silver"));
        spacedText(level,p,3.5,2.00,data.title(),1.47f,6.10,0x24242b,false);
        // Keep the shared ink bottom as the name shrinks and the level grows.
        double nameBottom=.833;
        rowText(level,p,.32,nameBottom+.1375,"Lv. "+data.level(),2.00f,1.05,0x000000,false);
        rowText(level,p,1.42,nameBottom+.2908125,data.name(),4.23f,3.68,0x000000,false);
        rowText(level,p,.32,.49,"RATING",1.184f,1.45,0xffffff,true);
        rowText(level,p,1.95,.49,data.rating(),1.52f,3.15,0xffffff,true);
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
                CommandFeedback.failure(source,Component.literal("请先在相同位置和朝向执行 /chunithm debug nameplate。"));return 0;
            }
            update(source.getLevel(),p,data);return 1;
        }catch(RuntimeException invalid){CommandFeedback.failure(source,Component.literal("名牌数据无效，请检查 JSON 字段、SILVER/GOLD 称号颜色和角色模型资源 ID。"));return 0;}
    }
}
