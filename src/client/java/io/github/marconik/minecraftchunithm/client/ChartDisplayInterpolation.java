package io.github.marconik.minecraftchunithm.client;

import com.mojang.math.Transformation;
import io.github.marconik.minecraftchunithm.ChartDisplayPose;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.world.entity.Display;

/** Linear interpolation of complete affine poses keeps every ribbon corner bounded. */
public final class ChartDisplayInterpolation {
    private static final Map<Display,Frame> FRAMES=new WeakHashMap<>();
    private static long checked;
    private static final class Frame {
        ChartDisplayPose previous,current;double start;
        Frame(ChartDisplayPose pose,double now){previous=current=pose;start=now-1;}
        ChartDisplayPose at(double now){return ChartDisplayPose.interpolate(previous,current,(float)(now-start));}
    }
    public static long checked(){return checked;}
    public static ChartDisplayPose sample(Display.ItemDisplay entity,float partialTick){
        var incoming=entity.getItemStack().get(ChartDisplayPose.TYPE);if(incoming==null)return null;
        double now=entity.tickCount+partialTick;
        var frame=FRAMES.computeIfAbsent(entity,key->new Frame(incoming,now));
        if(!incoming.equals(frame.current)){
            frame.previous=frame.at(now);frame.current=incoming;frame.start=entity.tickCount;
        }
        return frame.at(now);
    }
    public static void apply(Display entity,DisplayEntityRenderState state,float partialTick){
        if(!(entity instanceof Display.ItemDisplay item)||state.renderState==null)return;
        var pose=sample(item,partialTick);if(pose==null)return;
        // This also checks real client render subframes in the opt-in test.
        if(Boolean.getBoolean("minecraftchunithm.offTrackQuickPlay")){
            if(!pose.inside())throw new AssertionError("Client chart pose crossed the judgment stripe: "+pose);
            checked++;
        }
        var original=state.renderState;
        state.renderState=new Display.RenderState(Display.GenericInterpolator.constant(new Transformation(pose.matrix())),
                original.billboardConstraints(),original.brightnessOverride(),original.shadowRadius(),original.shadowStrength(),original.glowColorOverride());
        state.interpolationProgress=1;
    }
}
