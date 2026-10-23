package io.github.marconik.minecraftchunithm;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.joml.Vector3f;

/** Small recessed-looking sockets, rather than cables spanning empty space. */
final class PartPorts {
    private static final String TAG="minecraftchunithm.part_ports";
    private static Block headLogo;
    static void initialize(){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"head_control_logo"));
        headLogo=Registry.register(BuiltInRegistries.BLOCK,key,new Block(BlockBehaviour.Properties.of().setId(key)));
    }
    static boolean ready(){return headLogo!=null;}
    static boolean checkReady(net.minecraft.commands.CommandSourceStack source){
        if(ready())return true;
        source.sendFailure(net.minecraft.network.chat.Component.literal("控制入口资源尚未初始化。请保存世界并完全关闭 Minecraft，再启动最新版；仅退出世界或 F3+T 无法更新 Java 代码。"));
        MinecraftChunithm.LOGGER.error("Generation refused: PartPorts not initialized. A running development JVM may be using mixed build outputs.");
        return false;
    }
    static void remove(ServerLevel level,PartConnections.Part part){
        var p=part.placement;
        String owner=TAG+"."+p.origin().getX()+"_"+p.origin().getY()+"_"+p.origin().getZ();
        var box=new net.minecraft.world.phys.AABB(p.point(-.2,0,-.2),p.point(part.width()+.2,part.height()+.2,1.2));
        for(var e:level.getEntitiesOfClass(Display.class,box,e->e.entityTags().contains(owner)))e.discard();
    }
    static void draw(ServerLevel level,PartConnections.Part part){
        if(part.kind==PartConnections.Kind.HEAD&&!ready())throw new IllegalStateException("Head control logo was not initialized; fully restart the development client");
        remove(level,part);
        var p=part.placement;
        String owner=TAG+"."+p.origin().getX()+"_"+p.origin().getY()+"_"+p.origin().getZ();
        switch(part.kind){
            case HEAD->{
                for(double x:new double[]{1.5,5.5,9.5})socket(level,p,owner,x,.34,1.02,false);
                side(level,p,owner,0,2.5);side(level,p,owner,11,2.5);
                headWindow(level,p,owner,5.5,2.3);
            }
            case TRACK->{for(double x:new double[]{.5,4.5,8.5})socket(level,p,owner,x,.34,-.02,true);}
            case PLAYER->{side(level,p,owner,7,1.5);window(level,p,owner,3.5,1.5);}
            case SONG->{side(level,p,owner,0,1.5);window(level,p,owner,3.5,1.5);}
            case GROUND_SLIDE,AIR_LEFT,AIR_RIGHT->{}
        }
    }
    private static void socket(ServerLevel l,Placement p,String tag,double x,double y,double z,boolean back){
        cube(l,p,tag,x-.23,y-.17,z-.015,.46,.34,.03,"black_concrete");
        cube(l,p,tag,x-.13,y-.07,z+(back?-.025:.025),.26,.14,.015,"cyan_concrete");
    }
    private static void side(ServerLevel l,Placement p,String tag,double x,double y){
        cube(l,p,tag,x-.018,y-.22,.25,.036,.44,.5,"black_concrete");
        cube(l,p,tag,x-.025,y-.12,.36,.05,.24,.28,"cyan_concrete");
    }
    private static void window(ServerLevel l,Placement p,String tag,double x,double y){
        cube(l,p,tag,x-.6,y-.36,-.035,1.2,.72,.025,"gray_concrete");
        cube(l,p,tag,x-.52,y-.28,-.055,1.04,.56,.015,"cyan_concrete");
        cube(l,p,tag,x-.40,y-.06,-.075,.80,.12,.015,"black_concrete");
    }
    private static void headWindow(ServerLevel level,Placement p,String tag,double x,double y){
        cube(level,p,tag,x-.6,y-.36,-.035,1.2,.72,.025,"gray_concrete");
        cube(level,p,tag,x-.52,y-.28,-.055,1.04,.56,.015,"black_concrete");
        var logo=EntityTypes.BLOCK_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);if(logo==null)throw new IllegalStateException();
        // Native north-facing block UVs show the supplied image readable from
        // the head's rear. Preserve the original 380:164 aspect ratio.
        float width=.96f,height=width*164/380;
        var pos=p.point(x-width/2,y-height/2,-.075);logo.setPos(pos.x,pos.y,pos.z);logo.setYRot(p.yaw());
        logo.setBlockState(headLogo.defaultBlockState());
        logo.setTransformation(new Transformation(null,null,new Vector3f(width,height,.01f),null));
        logo.setBrightnessOverride(new net.minecraft.util.Brightness(15,15));logo.setWidth(2);logo.setHeight(1);
        logo.addTag(TAG);logo.addTag(tag);level.addFreshEntity(logo);
    }
    private static void cube(ServerLevel l,Placement p,String tag,double x,double y,double z,double w,double h,double d,String material){
        var e=EntityTypes.BLOCK_DISPLAY.create(l,EntitySpawnReason.TRIGGERED);if(e==null)throw new IllegalStateException();
        e.setBlockState(BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft",material)).defaultBlockState());
        var pos=p.point(x,y,z);e.setPos(pos.x,pos.y,pos.z);e.setYRot(p.yaw());
        e.setTransformation(new Transformation(null,null,new Vector3f((float)w,(float)h,(float)d),null));
        e.setBrightnessOverride(new net.minecraft.util.Brightness(15,15));e.setWidth(28);e.setHeight(5);e.addTag(TAG);e.addTag(tag);l.addFreshEntity(e);
    }
}
