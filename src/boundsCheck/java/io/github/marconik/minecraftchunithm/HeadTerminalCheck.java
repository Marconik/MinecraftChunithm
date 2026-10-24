package io.github.marconik.minecraftchunithm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class HeadTerminalCheck {
    static void run(){
        double previous=1;
        for(double layer:new double[]{1.001,1.014,1.025,1.035,1.06,1.075,1.09,1.095}){
            double depth=FacadeSurface.depth(layer);
            if(depth<=previous||depth>1.006)throw new AssertionError("Facade layer order or surface spacing mismatch");
            previous=depth;
        }
        int hits=0;
        for(var direction:Direction.Plane.HORIZONTAL){
            var head=new Placement(new BlockPos(-17,63,29),direction);
            for(double x:new double[]{4.72,5.5,6.28})for(double y:new double[]{1.9,2.3,2.7}){
                var shape=HeadTerminal.shape((int)x,(int)y,direction);
                var hit=shape.clip(head.point(x,y,-1),head.point(x,y,.9),head.block((int)x,(int)y,0));
                if(hit==null||hit.getDirection()!=direction.getOpposite()
                        ||hit.getLocation().distanceTo(head.point(x,y,HeadTerminal.DEPTH))>1e-6
                        ||!HeadTerminal.contains(head,hit.getLocation()))
                    throw new AssertionError("Rear opening/click target mismatch: "+direction+" at "+x+", "+y);
                hits++;
            }
            for(double[] point:new double[][]{{4.5,2.3},{6.5,2.3},{5.5,1.6},{5.5,3}}){
                double x=point[0],y=point[1];
                var shape=HeadTerminal.shape((int)x,(int)y,direction);
                var hit=shape.clip(head.point(x,y,-1),head.point(x,y,.9),head.block((int)x,(int)y,0));
                if(hit==null||hit.getLocation().distanceTo(head.point(x,y,0))>1e-6
                        ||HeadTerminal.contains(head,hit.getLocation()))
                    throw new AssertionError("Head surface outside the terminal must remain solid and inactive");
            }
        }
        System.out.println("Head terminal: "+hits+" recessed ray hits across three columns/four facings; surrounding surface and click boundaries passed.");
        for(boolean player:new boolean[]{true,false})for(var facing:Direction.Plane.HORIZONTAL){
            var placement=new Placement(new BlockPos(-17,63,29),facing);
            double width=HeadTerminal.sideWidth(player);
            for(double x:new double[]{3.5-width/2+.03,3.5,3.5+width/2-.03}){
                var cut=HeadTerminal.opening((int)x,1,3.5,1.5,width,1);
                var hit=HeadTerminal.shape(cut,facing).clip(placement.point(x,1.5,-1),placement.point(x,1.5,.9),placement.block((int)x,1,0));
                if(hit==null||hit.getLocation().distanceTo(placement.point(x,1.5,HeadTerminal.DEPTH))>1e-6
                        ||!HeadTerminal.contains(placement,hit.getLocation(),3.5,1.5,width,1))
                    throw new AssertionError("Side terminal recess/click mismatch");
            }
        }
        System.out.println("Both side-panel terminal widths: four-facing recessed geometry and three-column click targets passed.");
    }
}
