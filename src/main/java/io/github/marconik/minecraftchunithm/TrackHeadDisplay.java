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
    private TrackHeadDisplay(){}
    public static void update(ServerLevel level,Placement placement,HeadDisplayData data) {
        for(var entity:level.getEntitiesOfClass(Display.class,new AABB(placement.point(-.1,0,.9),placement.point(11.1,4.1,1.3)),e->e.entityTags().contains(TAG)))entity.discard();
        long[] counters={data.critical(),data.justice(),data.attack(),data.miss()};
        int[] colors={0xfff26c,0xffb631,0x58ed39,0xbbc0c7};
        for(int i=0;i<4;i++)text(level,placement,(i+.5)*2.75,3.5625,Long.toString(counters[i]),2f,colors[i],true);
        text(level,placement,2.60,2.66015625,"MAX COMBO:",1.5625f,0x252733,true);
        text(level,placement,6.60,2.66015625,"SCORE:",1.5625f,0x252733,true);
        text(level,placement,4.75,2.66015625,Long.toString(data.maxCombo()),1.5625f,0x252733,true);
        text(level,placement,8.75,2.66015625,String.format(Locale.ROOT,"%,d",data.score()),1.5625f,0x252733,true);
        panel(level,placement,1.5,208/128.0,8,73/128.0,"white_concrete",false,1.003);
        panel(level,placement,200/128.0,216/128.0,1008/128.0,57/128.0,"black_concrete",false,1.018);
        // Pixel coordinates match the native background texture (128 pixels per block).
        if(data.barFraction()>0)fill(level,placement,200/128.0,(512-296)/128.0,
                1008/128.0*data.barFraction(),57/128.0,data.goldBar());
        for(int i=0;i<10;i++) {
            double x=i<3?2+i*.55:3.65+(i-3)*.8;
            double width=i<3?.42:.65,height=i<3?.28:.36;
            panel(level,placement,x,1.115,width,height,"white_concrete",true,1.003);
            panel(level,placement,x+.045,1.15,width-.09,height-.07,
                    i<data.filledCells()?(i>=3?"yellow_concrete":"light_blue_concrete"):"gray_concrete",true,1.02);
        }
        text(level,placement,4.047,1.55,"CLEAR",.42f,0xfff7b3,true);
    }
    private static void text(ServerLevel level,Placement placement,double x,double centerY,String text,float scale,int color,boolean bold) {
        var entity=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        if(entity==null)throw new IllegalStateException("Cannot create text display");
        entity.setText(Component.literal(text).withStyle(style->style.withColor(color).withBold(bold)));
        entity.setLineWidth(4096);entity.setBackgroundColor(0);entity.setFlags((byte)0);
        entity.setTransformation(new Transformation(null,null,new Vector3f(scale),null));
        var pos=placement.point(x,centerY-scale*.1125,1.06);entity.setPos(pos.x,pos.y,pos.z);entity.setYRot(placement.yaw());
        entity.setBrightnessOverride(new Brightness(15,15));entity.addTag(TAG);level.addFreshEntity(entity);
    }
    private static void fill(ServerLevel level,Placement placement,double x,double y,double width,double height,boolean gold) {
        panel(level,placement,x,y,width,height,gold?"yellow_concrete":"light_blue_concrete",false,1.035);
    }
    private static void panel(ServerLevel level,Placement placement,double x,double y,double width,double height,String material,boolean slanted,double z) {
        var entity=EntityTypes.BLOCK_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        if(entity==null)throw new IllegalStateException("Cannot create progress display");
        entity.setBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft",material)).defaultBlockState());
        var matrix=new org.joml.Matrix4f().m00((float)width).m11((float)height).m22(.012f);
        if(slanted)matrix.m10((float)(height*.4));
        entity.setTransformation(new Transformation(matrix));
        var pos=placement.point(x,y,z);entity.setPos(pos.x,pos.y,pos.z);entity.setYRot(placement.yaw());
        entity.setBrightnessOverride(new Brightness(15,15));entity.addTag(TAG);level.addFreshEntity(entity);
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
