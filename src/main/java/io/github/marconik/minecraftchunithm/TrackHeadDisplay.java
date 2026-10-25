package io.github.marconik.minecraftchunithm;

import com.mojang.math.Transformation;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/** World-space data display, aligned with the SOUTH-facing 11x4 facade. */
public final class TrackHeadDisplay {
    private static final String TAG="minecraftchunithm.head_display";
    private static final String SURFACE_TAG=TAG+".surface2";
    private static final java.util.Map<String,net.minecraft.world.level.block.Block> CELL_BLOCKS=new java.util.HashMap<>();
    private TrackHeadDisplay(){}
    public static void update(ServerLevel level,Placement placement,HeadDisplayData data) {
        for(var entity:level.getEntitiesOfClass(Display.class,new AABB(placement.point(-.1,0,.9),placement.point(11.1,4.1,1.3)),e->e.entityTags().contains(TAG)))entity.discard();
        long[] counters={data.critical(),data.justice(),data.attack(),data.miss()};
        int[] colors={0xfff26c,0xffb631,0x58ed39,0xbbc0c7};
        for(int i=0;i<4;i++)text(level,placement,(i+.5)*2.75,3.5625,Long.toString(counters[i]),2f,colors[i],true).addTag(role("counter."+i));
        glowingText(level,placement,2.60,2.66015625,"MAX COMBO:");
        glowingText(level,placement,6.60,2.66015625,"SCORE:");
        glowingText(level,placement,4.75,2.66015625,Long.toString(data.maxCombo()),"combo");
        glowingText(level,placement,8.75,2.66015625,String.format(Locale.ROOT,"%,d",data.score()),"score");
        panel(level,placement,1.5,208/128.0,8,73/128.0,"white_concrete",false,1.003);
        panel(level,placement,200/128.0,216/128.0,1008/128.0,57/128.0,"black_concrete",false,1.018);
        // Pixel coordinates match the native background texture (128 pixels per block).
        if(data.barFraction()>0)fill(level,placement,200/128.0,(512-296)/128.0,
                1008/128.0*data.barFraction(),57/128.0,data.goldBar());
        DisplayFinish.polish(level,placement,TAG,200/128.0,216/128.0,1008/128.0,57/128.0,1.052,false,true);
        cells(level,placement,data.filledCells());
        text(level,placement,4.047,1.55,"CLEAR",.42f,0xfff7b3,true);
    }
    private static void cells(ServerLevel level,Placement placement,int filled){
        for(int i=0;i<10;i++) {
            double x=i<3?2+i*.55:3.65+(i-3)*.8;
            double width=i<3?.42:.65,height=i<3?.28:.36;
            var entity=EntityTypes.BLOCK_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
            if(entity==null)throw new IllegalStateException("Cannot create progress cell");
            String appearance=(i<3?"small_":"large_")+(i<filled?(i<3?"blue":"gold"):"empty");
            entity.setBlockState(CELL_BLOCKS.get(appearance).defaultBlockState());
            double fullWidth=width+height*.4;
            var pos=placement.point(x,1.115,FacadeSurface.depth(1.035));
            entity.setPos(pos.x,pos.y,pos.z);entity.setYRot(placement.yaw());
            entity.setTransformation(new Transformation(null,null,new Vector3f((float)fullWidth,(float)height,.01f*FacadeSurface.DEPTH_SCALE),null));
            entity.setBrightnessOverride(new Brightness(15,15));entity.setWidth(1);entity.setHeight(1);
            entity.addTag(TAG);entity.addTag(SURFACE_TAG);entity.addTag(role("cell."+i));level.addFreshEntity(entity);
        }
    }
    private static String role(String name){return TAG+".value."+name;}
    private static AABB displayBounds(Placement p){return new AABB(p.point(-.1,0,.9),p.point(11.1,4.1,1.3));}
    /** Update existing displays in place; scoring must not respawn the facade every tick. */
    public static void updateScore(ServerLevel level,Placement p,HeadDisplayData data){
        var displays=level.getEntitiesOfClass(Display.class,displayBounds(p),e->e.entityTags().contains(TAG));
        if(displays.stream().noneMatch(e->e.entityTags().contains(role("score")))){
            update(level,p,data);return; // One-time migration of existing saved facades.
        }
        String[] names={"counter.0","counter.1","counter.2","counter.3","combo","score"};
        String[] values={Long.toString(data.critical()),Long.toString(data.justice()),Long.toString(data.attack()),Long.toString(data.miss()),
            Long.toString(data.maxCombo()),String.format(Locale.ROOT,"%,d",data.score())};
        boolean hasFill=false;
        for(var entity:displays){
            if(entity instanceof Display.TextDisplay text)for(int i=0;i<names.length;i++)if(entity.entityTags().contains(role(names[i])))
                if(!text.getText().getString().equals(values[i]))text.setText(Component.literal(values[i]).withStyle(text.getText().getStyle()));
            if(entity instanceof Display.BlockDisplay block){
                for(int i=0;i<10;i++)if(entity.entityTags().contains(role("cell."+i))){
                    String appearance=(i<3?"small_":"large_")+(i<data.filledCells()?(i<3?"blue":"gold"):"empty");
                    var state=CELL_BLOCKS.get(appearance).defaultBlockState();if(!block.getBlockState().equals(state))block.setBlockState(state);
                }
                if(entity.entityTags().contains(role("fill"))){
                    hasFill=true;
                    var material=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft",data.goldBar()?"yellow_concrete":"light_blue_concrete")).defaultBlockState();
                    if(!block.getBlockState().equals(material))block.setBlockState(material);
                    block.setTransformation(new Transformation(new org.joml.Matrix4f().m00((float)Math.max(.000001,1008/128.0*data.barFraction()))
                        .m11(57/128f).m22(.012f*FacadeSurface.DEPTH_SCALE)));
                }
            }
        }
        if(!hasFill&&data.barFraction()>0)fill(level,p,200/128.0,216/128.0,1008/128.0*data.barFraction(),57/128.0,data.goldBar());
    }
    /** Migrate legacy cell displays without resetting the counters, score or current progress. */
    static void initialize(){
        for(String size:new String[]{"small","large"})for(String state:new String[]{"empty","blue","gold"}){
            String name=size+"_"+state;
            var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"progress_cell_"+name));
            CELL_BLOCKS.put(name,net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.BLOCK,key,
                    new net.minecraft.world.level.block.Block(CabinetBlocks.properties(key))));
        }
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(level->{
            if(level.getGameTime()%20!=0)return;
            for(var head:PartConnections.ofKind(level,PartConnections.Kind.HEAD)){
                if(PartConnections.integrity(level,head)!=PartConnections.Status.CONNECTED)continue;
                var p=head.placement;
                var old=level.getEntitiesOfClass(Display.class,new AABB(p.point(-.1,0,.9),p.point(11.1,4.1,1.3)),e->e.entityTags().contains(TAG));
                if(old.stream().noneMatch(e->e instanceof Display.TextDisplay&&!e.entityTags().contains(SURFACE_TAG)))continue;
                long[] values=new long[6];boolean[] found=new boolean[6];
                for(var entity:old)if(entity instanceof Display.TextDisplay text){
                    var delta=entity.position().subtract(p.point(0,0,0));
                    double x=delta.x*p.across().getStepX()+delta.z*p.across().getStepZ();
                    for(int i=0;i<6;i++){
                        double expectedX=i<4?(i+.5)*2.75:i==4?4.75:8.75;
                        double expectedY=i<4?3.5625-2*.1125:2.66015625-1.5625*.1125;
                        if(Math.abs(x-expectedX)<.0001&&Math.abs(delta.y-expectedY)<.0001)
                            try{values[i]=Long.parseLong(text.getText().getString().replace(",",""));found[i]=true;}catch(NumberFormatException ignored){}
                    }
                }
                boolean complete=true;for(boolean value:found)complete&=value;
                if(complete)update(level,p,new HeadDisplayData(values[0],values[1],values[2],values[3],values[4],values[5]));
            }
        });
    }
    private static Display.TextDisplay text(ServerLevel level,Placement placement,double x,double centerY,String text,float scale,int color,boolean bold) {
        return textLayer(level,placement,x,centerY,text,scale,color,bold,1.06,255);
    }
    private static void glowingText(ServerLevel level,Placement placement,double x,double centerY,String value) {
        glowingText(level,placement,x,centerY,value,"");
    }
    private static void glowingText(ServerLevel level,Placement placement,double x,double centerY,String value,String field) {
        // Two faint rings behind the ink produce a white halo rather than a
        // black drop shadow. Keep the existing dark font, scale and alignment.
        for(int i=0;i<8;i++) {
            double angle=i*Math.PI/4;
            textLayer(level,placement,x+Math.cos(angle)*.026,centerY+Math.sin(angle)*.026,
                    value,1.5625f,0xffffff,true,1.050,70).addTag(role(field));
        }
        for(int i=0;i<4;i++) {
            double angle=i*Math.PI/2;
            textLayer(level,placement,x+Math.cos(angle)*.013,centerY+Math.sin(angle)*.013,
                    value,1.5625f,0xffffff,true,1.055,170).addTag(role(field));
        }
        text(level,placement,x,centerY,value,1.5625f,0x252733,true).addTag(role(field));
    }
    private static Display.TextDisplay textLayer(ServerLevel level,Placement placement,double x,double centerY,String text,float scale,int color,boolean bold,double depth,int opacity) {
        var entity=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        if(entity==null)throw new IllegalStateException("Cannot create text display");
        entity.setText(Component.literal(text).withStyle(style->style.withColor(color).withBold(bold)));
        entity.setLineWidth(4096);entity.setBackgroundColor(0);entity.setFlags((byte)0);
        entity.setTextOpacity((byte)opacity);
        entity.setTransformation(new Transformation(null,null,new Vector3f(scale),null));
        var pos=placement.point(x,centerY-scale*.1125,FacadeSurface.depth(depth));entity.setPos(pos.x,pos.y,pos.z);entity.setYRot(placement.yaw());
        entity.setBrightnessOverride(new Brightness(15,15));entity.addTag(TAG);entity.addTag(SURFACE_TAG);level.addFreshEntity(entity);return entity;
    }
    private static void fill(ServerLevel level,Placement placement,double x,double y,double width,double height,boolean gold) {
        panel(level,placement,x,y,width,height,gold?"yellow_concrete":"light_blue_concrete",false,1.035).addTag(role("fill"));
    }
    private static Display.BlockDisplay panel(ServerLevel level,Placement placement,double x,double y,double width,double height,String material,boolean slanted,double z) {
        var entity=EntityTypes.BLOCK_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        if(entity==null)throw new IllegalStateException("Cannot create progress display");
        entity.setBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft",material)).defaultBlockState());
        var matrix=new org.joml.Matrix4f().m00((float)width).m11((float)height).m22(.012f*FacadeSurface.DEPTH_SCALE);
        if(slanted)matrix.m10((float)(height*.4));
        entity.setTransformation(new Transformation(matrix));
        var pos=placement.point(x,y,FacadeSurface.depth(z));entity.setPos(pos.x,pos.y,pos.z);entity.setYRot(placement.yaw());
        entity.setBrightnessOverride(new Brightness(15,15));entity.addTag(TAG);entity.addTag(SURFACE_TAG);level.addFreshEntity(entity);return entity;
    }
    public static int command(CommandSourceStack source,int critical,int justice,int attack,int miss,int combo,int score) {
        var placement=Placement.head(source);
        for(int row=0;row<4;row++)for(int col=0;col<11;col++) {
            var pos=placement.block(col,row,0);
            var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(source.getLevel().getBlockState(pos).getBlock());
            if(!id.toString().equals(MinecraftChunithm.MOD_ID+":head_"+col+"_"+row)) {
                source.sendFailure(Component.literal("请在生成 head 时相同的锚点更新显示。"));return 0;
            }
        }
        var data=new HeadDisplayData(critical,justice,attack,miss,combo,score);
        update(source.getLevel(),placement,data);
        source.sendSuccess(()->Component.literal("头部显示已更新："+data.percent()+"%"),false);return 1;
    }
}
