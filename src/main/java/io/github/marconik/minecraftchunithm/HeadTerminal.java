package io.github.marconik.minecraftchunithm;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared geometry for the rear terminal, its real recess and its click target. */
final class HeadTerminal {
    static final double X=5.5,Y=2.3,HEIGHT=1,WIDTH=5.0/3,DEPTH=1.0/8,BORDER=1.0/9;
    static final double LOGO_WIDTH=4.0/3;
    record Opening(double x0,double y0,double x1,double y1){}
    private HeadTerminal(){}
    static double sideWidth(boolean player){return player?1.75:2.1;}
    static Opening opening(int col,int row){
        return opening(col,row,X,Y,WIDTH,HEIGHT);
    }
    static Opening opening(int col,int row,double cx,double cy,double width,double height){
        double x0=Math.max(0,cx-width/2-col),x1=Math.min(1,cx+width/2-col);
        double y0=Math.max(0,cy-height/2-row),y1=Math.min(1,cy+height/2-row);
        return x0<x1&&y0<y1?new Opening(x0,y0,x1,y1):null;
    }
    static VoxelShape shape(int col,int row,Direction forward){
        return shape(opening(col,row),forward);
    }
    static VoxelShape shape(Opening cut,Direction forward){
        if(cut==null)return Shapes.block();
        return Shapes.or(box(forward,0,0,DEPTH,1,1,1),
                box(forward,0,0,0,cut.x0(),1,DEPTH),box(forward,cut.x1(),0,0,1,1,DEPTH),
                box(forward,cut.x0(),0,0,cut.x1(),cut.y0(),DEPTH),
                box(forward,cut.x0(),cut.y1(),0,cut.x1(),1,DEPTH));
    }
    private static VoxelShape box(Direction direction,double x0,double y0,double z0,double x1,double y1,double z1){
        if(x0>=x1||y0>=y1||z0>=z1)return Shapes.empty();
        var across=direction.getCounterClockWise();
        double ax=.5+across.getStepX()*(x0-.5)+direction.getStepX()*(z0-.5);
        double az=.5+across.getStepZ()*(x0-.5)+direction.getStepZ()*(z0-.5);
        double bx=.5+across.getStepX()*(x1-.5)+direction.getStepX()*(z1-.5);
        double bz=.5+across.getStepZ()*(x1-.5)+direction.getStepZ()*(z1-.5);
        return Shapes.box(Math.min(ax,bx),y0,Math.min(az,bz),Math.max(ax,bx),y1,Math.max(az,bz));
    }
    static boolean contains(Placement head,Vec3 hit){
        return contains(head,hit,X,Y,WIDTH,HEIGHT);
    }
    static boolean contains(Placement head,Vec3 hit,double cx,double cy,double width,double height){
        var delta=hit.subtract(head.point(0,0,0));
        double x=delta.x*head.across().getStepX()+delta.z*head.across().getStepZ();
        double z=delta.x*head.forward().getStepX()+delta.z*head.forward().getStepZ();
        return Math.abs(x-cx)<=width/2+.0001&&Math.abs(delta.y-cy)<=height/2+.0001
                &&z>=-.001&&z<=DEPTH+.001;
    }
}
