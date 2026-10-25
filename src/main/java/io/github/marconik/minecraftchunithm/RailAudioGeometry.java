package io.github.marconik.minecraftchunithm;

import net.minecraft.world.phys.Vec3;

/** A line emitter uses the nearest point of each rail, without duplicating music sources. */
public final class RailAudioGeometry {
    private RailAudioGeometry(){}
    public static Vec3 position(Placement track,int length,Vec3 listener,boolean left){
        var delta=listener.subtract(track.point(0,0,0));
        double along=delta.x*track.forward().getStepX()+delta.z*track.forward().getStepZ();
        // The player faces against track.forward(), towards the head: +local X is their right.
        return track.point(left?.25:8.75,.75,Math.clamp(along,0,length));
    }
}
