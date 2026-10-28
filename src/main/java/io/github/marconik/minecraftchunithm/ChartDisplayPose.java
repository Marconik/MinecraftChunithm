package io.github.marconik.minecraftchunithm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** One atomic affine pose: no independent entity-position and shape timelines. */
public record ChartDisplayPose(float x,float y,float z,float sx,float sy,float sz,float shear,float lift,
                               float depth,float limit,long frame) {
    public static DataComponentType<ChartDisplayPose> TYPE;
    public static final Codec<ChartDisplayPose> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.FLOAT.fieldOf("x").forGetter(ChartDisplayPose::x),Codec.FLOAT.fieldOf("y").forGetter(ChartDisplayPose::y),
            Codec.FLOAT.fieldOf("z").forGetter(ChartDisplayPose::z),Codec.FLOAT.fieldOf("sx").forGetter(ChartDisplayPose::sx),
            Codec.FLOAT.fieldOf("sy").forGetter(ChartDisplayPose::sy),Codec.FLOAT.fieldOf("sz").forGetter(ChartDisplayPose::sz),
            Codec.FLOAT.fieldOf("shear").forGetter(ChartDisplayPose::shear),Codec.FLOAT.fieldOf("lift").forGetter(ChartDisplayPose::lift),
            Codec.FLOAT.fieldOf("depth").forGetter(ChartDisplayPose::depth),Codec.FLOAT.fieldOf("limit").forGetter(ChartDisplayPose::limit),
            Codec.LONG.fieldOf("frame").forGetter(ChartDisplayPose::frame)).apply(i,ChartDisplayPose::new));
    public static final StreamCodec<FriendlyByteBuf,ChartDisplayPose> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeFloat(p.x);b.writeFloat(p.y);b.writeFloat(p.z);b.writeFloat(p.sx);b.writeFloat(p.sy);b.writeFloat(p.sz);
        b.writeFloat(p.shear);b.writeFloat(p.lift);b.writeFloat(p.depth);b.writeFloat(p.limit);b.writeVarLong(p.frame);
    },b->new ChartDisplayPose(b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),
            b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readVarLong()));
    public ChartDisplayPose {
        for(float v:new float[]{x,y,z,sx,sy,sz,shear,lift,depth,limit})if(!Float.isFinite(v))throw new IllegalArgumentException("Nonfinite chart pose");
        if(sx<=0||sy<=0||sz<=0||depth<=0||limit<=0)throw new IllegalArgumentException("Invalid chart pose size");
    }
    public static void initialize(){
        TYPE=Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,Identifier.parse("minecraftchunithm:chart_pose"),
                DataComponentType.<ChartDisplayPose>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build());
    }
    static ChartDisplayPose of(ChartPlayback.Spec s,int length,long frame){
        float depth=s.item().endsWith("_body")?1:s.item().equals("beat_line")||s.item().equals("speed_change_line")?.03125f:.5f;
        return new ChartDisplayPose((float)s.x(),(float)s.y(),(float)s.z(),(float)s.sx(),(float)s.sy(),(float)s.sz(),
                (float)s.shear(),(float)s.lift(),depth,(float)RhythmTiming.judgment(length),frame);
    }
    public Matrix4f matrix(){return new Matrix4f().translation(x,y,z).scale(sx,sy,sz).m20(shear).m21(lift);}
    public static ChartDisplayPose interpolate(ChartDisplayPose a,ChartDisplayPose b,float progress){
        float p=Math.clamp(progress,0,1);
        if(a.depth!=b.depth||a.limit!=b.limit)return b;
        return new ChartDisplayPose(mix(a.x,b.x,p),mix(a.y,b.y,p),mix(a.z,b.z,p),mix(a.sx,b.sx,p),mix(a.sy,b.sy,p),
                mix(a.sz,b.sz,p),mix(a.shear,b.shear,p),mix(a.lift,b.lift,p),b.depth,b.limit,b.frame);
    }
    private static float mix(float a,float b,float p){return (float)((1.0-p)*a+(double)p*b);}
    public boolean inside(){return z-depth*sz/2>=-.0002f&&z+depth*sz/2<=limit+.0002f;}
}
