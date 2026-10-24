package io.github.marconik.minecraftchunithm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class HeadTerminalCheck {
    static void run(){
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
    }
}
