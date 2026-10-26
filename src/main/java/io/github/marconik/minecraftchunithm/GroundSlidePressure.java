package io.github.marconik.minecraftchunithm;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/** Combined contact/playback colour effect and the same 16 channels for the head. */
final class GroundSlidePressure {
    private GroundSlidePressure(){}
    static void initialize(){ServerTickEvents.END_LEVEL_TICK.register(GroundSlidePressure::tick);}

    // Like a light weighted pressure plate, a single eligible entity is enough.
    // The union of four adjacent 0.5x0.5 cells is one 0.5x2 strip, with no gaps.
    static AABB strip(Placement placement,int lane){
        if(lane<0||lane>=16)throw new IllegalArgumentException("Invalid Ground Slide lane");
        double left=(GroundSlide.WIDTH-GroundSlide.LIGHT_WIDTH)/2.0+lane*.5;
        return new AABB(placement.point(left,GroundSlide.SURFACE_HEIGHT,.5),
                placement.point(left+.5,GroundSlide.SURFACE_HEIGHT+.25,2.5));
    }

    static int contacts(Placement placement,List<AABB> entities){
        int mask=0;
        for(int lane=0;lane<16;lane++){
            var area=strip(placement,lane);
            for(var entity:entities)if(area.intersects(entity)){mask|=1<<lane;break;}
        }
        return mask;
    }

    static int tileMask(int column,int lanes){
        int mask=0;
        for(int half=0;half<2;half++){
            int lane=column*2+half-(GroundSlide.WIDTH-GroundSlide.LIGHT_WIDTH);
            if(lane>=0&&lane<16&&(lanes&(1<<lane))!=0)mask|=1<<half;
        }
        return mask;
    }

    static double[] responses(ServerLevel level,Placement placement){
        double[] response=new double[16];
        for(int lane=0;lane<16;lane++){
            int half=lane+GroundSlide.WIDTH-GroundSlide.LIGHT_WIDTH;
            int x=half/2;
            var state=level.getBlockState(placement.block(x,0,1));
            if(GroundSlide.matchesTile(state,x,1,placement.forward()))
                response[lane]=(state.getValue(GroundSlideBlock.PRESSED)&(1<<(half%2)))!=0?1:0;
        }
        return response;
    }

    private static void tick(ServerLevel level){
        for(var part:PartConnections.ofKind(level,PartConnections.Kind.GROUND_SLIDE)){
            var status=PartConnections.integrity(level,part);
            if(status==PartConnections.Status.UNLOADED)continue; // Never load distant chunks.
            int mask=0;
            if(status==PartConnections.Status.CONNECTED){
                var area=strip(part.placement,0).minmax(strip(part.placement,15));
                var entities=level.getEntitiesOfClass(Entity.class,area,
                        EntitySelector.NO_SPECTATORS.and(entity->!entity.isIgnoringBlockTriggers()));
                mask=contacts(part.placement,entities.stream().map(Entity::getBoundingBox).toList());
                mask|=ChartPlayback.groundSlideMask(level,part.placement);
            }
            // Refresh every loaded intact tile even when no entity remains, including after reload.
            for(int z=0;z<GroundSlide.DEPTH;z++)for(int x=0;x<GroundSlide.WIDTH;x++){
                var pos=part.placement.block(x,0,z);
                var state=level.getBlockState(pos);
                if(!GroundSlide.matchesTile(state,x,z,part.placement.forward()))continue;
                int pressed=tileMask(x,mask);
                if(state.getValue(GroundSlideBlock.PRESSED)!=pressed)
                    level.setBlock(pos,state.setValue(GroundSlideBlock.PRESSED,pressed),Block.UPDATE_CLIENTS);
            }
        }
    }
}
