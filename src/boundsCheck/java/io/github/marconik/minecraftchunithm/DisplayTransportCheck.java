package io.github.marconik.minecraftchunithm;

import com.google.gson.JsonParser;
import com.mojang.math.Transformation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;

/** Inspect the actual vanilla matrix decomposition/interpolation, not just server specs. */
public final class DisplayTransportCheck {
    private static Transformation shape(ChartPlayback.Spec s){
        return new Transformation(new Matrix4f().scaling((float)s.sx(),(float)s.sy(),(float)s.sz()).m20((float)s.shear()).m21((float)s.lift()));
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        ChartDefinition chart;
        try(var in=DisplayTransportCheck.class.getResourceAsStream("/data/minecraftchunithm/songs/2999/ultima.json")){
            chart=ChartDefinition.fromJson(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
        }
        long pairs=0,escaped=0,affineFrames=0;double worst=0,reconstruction=0,splitWorst=0;String example="",splitExample="";
        for(double begin:new double[]{0,30000,140000,160000,170000}){
            Map<String,ChartPlayback.Spec> before=ChartPlayback.checkFrame(chart,20,false,begin);
            for(double time=begin+50;time<Math.min(chart.lastTimeMs(),begin+5000);time+=50){
                var after=ChartPlayback.checkFrame(chart,20,false,time);
                for(var entry:before.entrySet()){
                    var a=entry.getValue();var b=after.get(entry.getKey());
                    if(b==null||!a.item().endsWith("_body"))continue;
                    var from=shape(a);var to=shape(b);
                    var rawA=ChartDisplayPose.of(a,24,0);var rawB=ChartDisplayPose.of(b,24,1);
                    for(float progress:new float[]{0,.1f,.25f,.5f,.75f,.9f,1}){
                        var pose=ChartDisplayPose.interpolate(rawA,rawB,progress);var affine=pose.matrix();
                        double half=(Math.abs(affine.m02())+Math.abs(affine.m12())+Math.abs(affine.m22()))/2;
                        if(!pose.inside()||affine.m32()-half<-.0002||affine.m32()+half>23.5002)
                            throw new AssertionError("Affine body crossed stripe: "+pose);
                        affineFrames++;
                    }
                    if(pairs==0){
                        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                        try{ChartDisplayPose.STREAM_CODEC.encode(buffer,rawB);
                            if(!rawB.equals(ChartDisplayPose.STREAM_CODEC.decode(buffer)))throw new AssertionError("Pose packet changed fields");
                        }finally{buffer.release();}
                        var json=ChartDisplayPose.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,rawB).getOrThrow();
                        if(!rawB.equals(ChartDisplayPose.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow()))throw new AssertionError("Pose save codec mismatch");
                    }
                    var rebuilt=new Transformation(to.translation(),to.leftRotation(),to.scale(),to.rightRotation()).getMatrix();
                    reconstruction=Math.max(reconstruction,Math.abs(rebuilt.m22()-b.sz()));
                    for(float p:new float[]{.1f,.25f,.5f,.75f,.9f}){
                        var m=from.slerp(to,p).getMatrix();double center=a.z()+(b.z()-a.z())*p;
                        double half=(Math.abs(m.m02())+Math.abs(m.m12())+Math.abs(m.m22()))/2;
                        double excess=Math.max(0,Math.max(half-center,center+half-24));
                        if(excess>1e-3)escaped++;
                        if(excess>worst){worst=excess;example=time+" "+entry.getKey()+" partial="+p+" old="+a+" new="+b;}
                        for(double positionProgress:new double[]{0,.25,.5,.75,1}){
                            double splitCenter=a.z()+(b.z()-a.z())*positionProgress;
                            double split=Math.max(0,Math.max(half-splitCenter,splitCenter+half-24));
                            if(split>splitWorst){splitWorst=split;splitExample=time+" "+entry.getKey()+" position="+positionProgress+" shape="+p+" old="+a+" new="+b;}
                        }
                    }
                    pairs++;
                }
                before=after;
            }
        }
        System.out.println("Vanilla display probe: pairs="+pairs+", escaped subframes="+escaped+", worst="+worst+", reconstruction error="+reconstruction+", example="+example);
        System.out.println("Independent position/shape progress: worst="+splitWorst+", example="+splitExample);
        if(pairs==0||escaped==0||splitWorst<1)throw new AssertionError("Did not reproduce unsafe vanilla transport");
        System.out.println("Atomic affine transport: "+affineFrames+" actual matrix subframes remained within the judgment stripe; packet/save codecs passed.");
    }
}
