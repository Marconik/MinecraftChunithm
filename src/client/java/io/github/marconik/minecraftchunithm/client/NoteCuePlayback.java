package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.AudioFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/** One replaceable pair of mono emitters; sub-tick note times are checked every visual frame. */
public final class NoteCuePlayback {
    private static final Identifier RESOURCE=Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"sounds/note/tap_critical.ogg");
    private static final SoundEvent EVENT=SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"note.tap_critical"));
    private static NoteCueAudio.State state;
    private static long timelineSession,received,lastPump;
    private static int total;
    private static final List<Double> assembling=new ArrayList<>();
    private static NoteCueTimeline.Cursor cursor;
    private static CompletableFuture<Pcm> decoded;
    private static boolean failed;
    private static CueSound left,right;
    private static long triggers,replacements;
    private NoteCuePlayback(){}
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(NoteCueAudio.Timeline.TYPE,(s,c)->c.client().execute(()->timeline(s)));
        ClientPlayNetworking.registerGlobalReceiver(NoteCueAudio.State.TYPE,(s,c)->c.client().execute(()->receive(s)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(NoteCuePlayback::pump);
        LevelExtractionEvents.END_EXTRACTION.register(context->pump(Minecraft.getInstance()));
    }
    private static void timeline(NoteCueAudio.Timeline s){
        if(s.offset()==0){stop();state=null;timelineSession=s.session();total=s.total();assembling.clear();cursor=null;}
        if(s.session()!=timelineSession||s.total()!=total||s.offset()!=assembling.size())return;
        for(double time:s.times()){
            if(!Double.isFinite(time)||time<0||!assembling.isEmpty()&&time<=assembling.getLast())return;
            assembling.add(time);
        }
        if(assembling.size()==total){cursor=new NoteCueTimeline.Cursor(assembling);prepare();}
    }
    private static void prepare(){
        if(decoded!=null)return;
        var manager=Minecraft.getInstance().getResourceManager();
        decoded=CompletableFuture.supplyAsync(()->{
            try(var input=manager.open(RESOURCE);var stream=new JOrbisAudioStream(input)){
                var format=stream.getFormat();
                if(format.getChannels()!=1||format.getSampleSizeInBits()!=16||format.isBigEndian())throw new IOException("Note cue must be signed 16-bit mono little-endian audio");
                var buffer=stream.readAll();if(buffer.remaining()>4*1024*1024)throw new IOException("Note cue exceeds 4 MiB");
                byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return new Pcm(bytes,format.getSampleRate());
            }catch(IOException error){throw new CompletionException(error);}
        },Util.nonCriticalIoPool());
    }
    private static boolean valid(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static void receive(NoteCueAudio.State s){
        var client=Minecraft.getInstance();
        if(client.level==null||!client.level.dimension().identifier().toString().equals(s.dimension()))return;
        if(s.session()==0){clear();return;}
        if(s.session()!=timelineSession||cursor==null||!Double.isFinite(s.time())||s.mask()<0||s.mask()>3||!valid(s.left())||!valid(s.right()))return;
        if(state==null||state.session()!=s.session()||state.revision()!=s.revision()){
            stop();cursor.reset(s.time());
        }else{
            if((s.mask()&1)==0)stopSide(true);
            if((s.mask()&2)==0)stopSide(false);
        }
        state=s;received=System.nanoTime();lastPump=received;
        if(!s.playing())stop();
    }
    private static double time(long now){return state.time()+(state.playing()?(now-received)/1_000_000.0:0);}
    private static void pump(Minecraft client){
        if(state==null)return;
        if(client.level==null||!client.level.dimension().identifier().toString().equals(state.dimension())){clear();return;}
        long now=System.nanoTime();
        if(client.isPaused()){received+=now-lastPump;lastPump=now;return;}
        lastPump=now;
        if(!state.playing()||cursor==null)return;
        double latest=cursor.poll(time(now));
        if(Double.isNaN(latest))return;
        // Never defer old notes until an asset finishes loading or a listener approaches.
        stop();
        if(decoded==null||!decoded.isDone()||failed)return;
        if(decoded.isCompletedExceptionally()){
            failed=true;decoded.whenComplete((pcm,error)->MinecraftChunithm.LOGGER.warn("Cannot decode note cue",error));return;
        }
        var listener=client.gameRenderer.mainCamera().position();var pcm=decoded.join();
        if((state.mask()&1)!=0&&listener.distanceToSqr(state.left())<=64*64)left=new CueSound(pcm,state.left());
        if((state.mask()&2)!=0&&listener.distanceToSqr(state.right())<=64*64)right=new CueSound(pcm,state.right());
        // Both requests use the same decoded PCM and are submitted in the same frame.
        if(left!=null)client.getSoundManager().play(left);
        if(right!=null)client.getSoundManager().play(right);
        if(left!=null||right!=null)triggers++;
    }
    private static void stopSide(boolean first){
        var sound=first?left:right;if(sound!=null){sound.finish();Minecraft.getInstance().getSoundManager().stop(sound);}
        if(first)left=null;else right=null;
    }
    private static void stop(){
        var manager=Minecraft.getInstance().getSoundManager();
        if(left!=null&&manager.isActive(left)||right!=null&&manager.isActive(right))replacements++;
        stopSide(true);stopSide(false);
    }
    private static void clear(){stop();state=null;cursor=null;timelineSession=0;assembling.clear();}
    static double checkTime(){return state==null?Double.NaN:time(System.nanoTime());}
    static long checkTriggers(){return triggers;}
    static long checkReplacements(){return replacements;}
    static void verifyNative(boolean active){
        var manager=Minecraft.getInstance().getSoundManager();
        if(active){
            if(left==null||right==null||!manager.isActive(left)||!manager.isActive(right)||left.opened==0||right.opened==0)
                throw new AssertionError("Both native note cue streams must be active");
            if(!left.spatialOneShot()||!right.spatialOneShot()||left.pcm!=right.pcm)
                throw new AssertionError("Note cues must share a non-looping mono sample at two world positions");
            if(left.position().distanceToSqr(state.left())>1e-8||right.position().distanceToSqr(state.right())>1e-8)
                throw new AssertionError("Note cues escaped the AIR sensor faces");
        }else if(left!=null||right!=null)throw new AssertionError("Note cues must stop on pause/stop/unload");
        MinecraftChunithm.LOGGER.info("Note cue native check passed: active={}, triggers={}, replacements={}",active,triggers,replacements);
    }
    private record Pcm(byte[] bytes,float rate){}
    private static final class CueSound extends AbstractTickableSoundInstance {
        private final Pcm pcm;private volatile int opened;
        CueSound(Pcm pcm,Vec3 point){
            super(EVENT,SoundSource.BLOCKS,SoundInstance.createUnseededRandom());this.pcm=pcm;
            relative=false;looping=false;volume=.2f;pitch=1;x=point.x;y=point.y;z=point.z;
        }
        @Override public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library,Identifier resource,boolean looping){
            opened++;
            return CompletableFuture.completedFuture(new AudioStream(){
                private int offset;
                public AudioFormat getFormat(){return new AudioFormat(pcm.rate,16,1,true,false);}
                public ByteBuffer read(int expected){
                    int size=Math.min(expected&~1,pcm.bytes.length-offset);var buffer=ByteBuffer.allocateDirect(size);
                    buffer.put(pcm.bytes,offset,size);offset+=size;return buffer.flip();
                }
                public void close(){}
            });
        }
        @Override public void tick(){}
        boolean spatialOneShot(){return !relative&&!looping;}
        Vec3 position(){return new Vec3(x,y,z);}
        void finish(){stop();}
    }
}
