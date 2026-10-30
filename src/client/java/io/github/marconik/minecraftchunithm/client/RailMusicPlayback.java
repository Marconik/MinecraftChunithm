package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/** Two native positional mono streams, preserving the original stereo channel samples. */
public final class RailMusicPlayback {
    private static RailAudio.Snapshot snapshot;
    private static long received,lastTick;
    private static String cachedResource;
    private static CompletableFuture<Pcm> decoded;
    private static RailSound left,right;
    private static long playingSession,playingRevision;
    private static long retryAfter;
    private static boolean failed;
    private RailMusicPlayback(){}
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(RailAudio.Snapshot.TYPE,(s,c)->c.client().execute(()->receive(s)));
        ClientTickEvents.END_CLIENT_TICK.register(RailMusicPlayback::tick);
    }
    private static void receive(RailAudio.Snapshot s){
        var client=Minecraft.getInstance();
        if(client.level==null||!client.level.dimension().identifier().toString().equals(s.dimension()))return;
        if(s.session()==0){clear();return;}
        if(!Double.isFinite(s.time())||!Double.isFinite(s.offset())||!Double.isFinite(s.duration())||s.length()<2||s.length()>1024||s.direction()<0||s.direction()>3)return;
        if(snapshot==null||snapshot.session()!=s.session()||snapshot.revision()!=s.revision())stop();
        snapshot=s;received=System.nanoTime();lastTick=received;
        if(!s.playing())stop();
    }
    private static double audioTime(){return snapshot.time()-snapshot.offset()+(snapshot.playing()?(System.nanoTime()-received)/1_000_000.0:0);}
    private static Placement track(){return new Placement(new BlockPos(snapshot.x(),snapshot.y(),snapshot.z()),Direction.from2DDataValue(snapshot.direction()));}
    private static Vec3 listener(){return Minecraft.getInstance().gameRenderer.mainCamera().position();}
    private static void tick(Minecraft client){
        if(snapshot==null)return;
        if(client.level==null||!client.level.dimension().identifier().toString().equals(snapshot.dimension())){clear();return;}
        long now=System.nanoTime();
        if(client.isPaused()){received+=now-lastTick;lastTick=now;return;}
        lastTick=now;
        var source=RailAudioGeometry.position(track(),snapshot.length(),listener(),true);
        if(source.distanceToSqr(listener())>64*64){stop();return;}
        if(!snapshot.resource().equals(cachedResource)){
            stop();cachedResource=snapshot.resource();failed=false;
            try{
                var resource=Identifier.parse(cachedResource);var manager=client.getResourceManager();
                decoded=CompletableFuture.supplyAsync(()->{
                    try(var input=manager.open(resource);var stream=new JOrbisAudioStream(input)){
                        var format=stream.getFormat();
                        if((format.getChannels()!=1&&format.getChannels()!=2)||format.getSampleSizeInBits()!=16||format.isBigEndian())throw new IOException("Expected mono/stereo signed 16-bit little-endian audio");
                        var buffer=stream.readAll();
                        if(buffer.remaining()>128*1024*1024)throw new IOException("Song exceeds 128 MiB decoded limit");
                        byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
                        var pcm=new Pcm(bytes,format.getChannels(),format.getSampleRate());
                        if(Boolean.getBoolean("minecraftchunithm.inputQuickPlay"))verifySplit(pcm);
                        return pcm;
                    }catch(IOException e){throw new CompletionException(e);}
                },Util.nonCriticalIoPool());
            }catch(RuntimeException error){failed=true;MinecraftChunithm.LOGGER.warn("Cannot prepare rail music",error);}
        }
        if(decoded==null||!decoded.isDone()||failed)return;
        if(decoded.isCompletedExceptionally()){
            failed=true;decoded.whenComplete((pcm,error)->MinecraftChunithm.LOGGER.warn("Cannot decode rail music {}",cachedResource,error));return;
        }
        double time=audioTime();var pcm=decoded.join();
        if(!snapshot.playing()||time<0||time>=pcm.duration()){stop();return;}
        if(left!=null&&(playingSession!=snapshot.session()||playingRevision!=snapshot.revision()
                ||!client.getSoundManager().isActive(left)||!client.getSoundManager().isActive(right)
                ||Math.abs(left.cursorTime()-time)>200))stop();
        if(left==null&&now>=retryAfter){
            long frame=Math.max(0,(long)(time*pcm.rate/1000));
            var event=SoundEvent.createVariableRangeEvent(Identifier.parse(snapshot.event()));
            left=new RailSound(event,pcm,frame,true,track(),snapshot.length());
            right=new RailSound(event,pcm,frame,false,track(),snapshot.length());
            playingSession=snapshot.session();playingRevision=snapshot.revision();
            var leftResult=client.getSoundManager().play(left);var rightResult=client.getSoundManager().play(right);
            if(leftResult==SoundEngine.PlayResult.NOT_STARTED||rightResult==SoundEngine.PlayResult.NOT_STARTED){stop();retryAfter=now+1_000_000_000L;return;}
            MinecraftChunithm.LOGGER.debug("Rail stereo started: {} at {} ms; player left={}, right={}",snapshot.event(),time,left.position(),right.position());
        }
    }
    private static void stop(){
        var manager=Minecraft.getInstance().getSoundManager();
        if(left!=null){left.finish();manager.stop(left);}
        if(right!=null){right.finish();manager.stop(right);}
        left=null;right=null;
    }
    private static void clear(){stop();snapshot=null;cachedResource=null;decoded=null;failed=false;}
    static void verifyNative(boolean active,double minimumTime){
        var manager=Minecraft.getInstance().getSoundManager();
        if(active){
            if(left==null||right==null||!manager.isActive(left)||!manager.isActive(right)||left.opened==0||right.opened==0)
                throw new AssertionError("Both native positional mono sources must be active");
            if(left.frame!=right.frame||left.cursorTime()<minimumTime)throw new AssertionError("Rail channel cursor mismatch");
        }else if(left!=null||right!=null)throw new AssertionError("Rail audio must stop on pause/stop");
        MinecraftChunithm.LOGGER.info("Rail audio native check passed: active={}, minimum seek={} ms",active,minimumTime);
    }
    private static void verifySplit(Pcm pcm){
        for(boolean left:new boolean[]{true,false})for(int at:new int[]{0,12345}){
            var stream=new MonoStream(pcm,at,left);var bytes=stream.read(1024);
            if(stream.getFormat().getChannels()!=1)throw new AssertionError("Rail stream must be mono");
            double gain=AudioVolumeSettings.gain(false);
            for(int i=0;i<bytes.remaining();i+=2){
                int channel=left||pcm.channels==1?0:1;
                int original=((at+i/2)*pcm.channels+channel)*2;
                short expected=AudioVolume.amplify((short)((pcm.bytes[original]&255)|(pcm.bytes[original+1]<<8)),gain);
                if(bytes.get(i)!=(byte)expected||bytes.get(i+1)!=(byte)(expected>>8))throw new AssertionError("Stereo channel separation/gain mismatch");
            }
        }
        MinecraftChunithm.LOGGER.info("Rail PCM check passed: both mono channels preserve channel selection, gain and seek frames");
    }
    static void verifyVolumeMix(){
        var saved=AudioVolumeSettings.levels();
        var pcm=new Pcm(new byte[]{0,16,0,-16,0,32,0,-32},2,48000);
        try{
            for(double gain:new double[]{0,.5,1,1.5,2}){
                AudioVolumeSettings.set(false,gain);
                for(boolean side:new boolean[]{true,false}){
                    var bytes=new MonoStream(pcm,0,side).read(4);
                    for(int frame=0;frame<2;frame++){
                        short source=(short)((side?1:-1)*(frame+1)*4096);
                        short expected=AudioVolume.amplify(source,gain);
                        if(bytes.get(frame*2)!=(byte)expected||bytes.get(frame*2+1)!=(byte)(expected>>8))throw new AssertionError("Native rail PCM gain mismatch");
                    }
                    var sound=new RailSound(SoundEvent.createVariableRangeEvent(Identifier.parse("minecraftchunithm:test")),pcm,0,side,
                            new Placement(BlockPos.ZERO,Direction.NORTH),24);
                    if(Math.abs(sound.getVolume()-.8*Math.min(1,gain))>1e-6)throw new AssertionError("Native rail volume mismatch");
                }
            }
        }finally{AudioVolumeSettings.set(false,saved.system());}
        MinecraftChunithm.LOGGER.info("Audio mixer native music streams: stereo channels, mute, attenuation and amplification passed");
    }
    private record Pcm(byte[] bytes,int channels,float rate){
        double duration(){return bytes.length/(channels*2.0)/rate*1000;}
    }
    /** Frame-aligned channel selection, with no downmix, re-encoding or resampling. */
    private static final class MonoStream implements AudioStream {
        private final Pcm pcm;private final int channel;private int frame;
        MonoStream(Pcm pcm,long frame,boolean left){this.pcm=pcm;this.frame=(int)Math.min(frame,pcm.bytes.length/(pcm.channels*2));channel=left||pcm.channels==1?0:1;}
        public AudioFormat getFormat(){return new AudioFormat(pcm.rate,16,1,true,false);}
        public ByteBuffer read(int expected){
            int count=Math.min(expected/2,pcm.bytes.length/(pcm.channels*2)-frame);
            var output=ByteBuffer.allocateDirect(count*2);
            double gain=AudioVolumeSettings.gain(false);
            for(int i=0;i<count;i++){
                int index=((frame++)*pcm.channels+channel)*2;
                short sample=AudioVolume.amplify((short)((pcm.bytes[index]&255)|(pcm.bytes[index+1]<<8)),gain);
                output.put((byte)sample).put((byte)(sample>>8));
            }
            return output.flip();
        }
        public void close(){}
    }
    private static final class RailSound extends AbstractTickableSoundInstance {
        private final Pcm pcm;private final long frame;private final boolean left;
        private final Placement track;private final int length;private final long started=System.nanoTime();
        private volatile int opened;
        RailSound(SoundEvent event,Pcm pcm,long frame,boolean left,Placement track,int length){
            super(event,SoundSource.RECORDS,SoundInstance.createUnseededRandom());
            this.pcm=pcm;this.frame=frame;this.left=left;this.track=track;this.length=length;
            relative=false;looping=false;volume=.8f;pitch=1;tick();
        }
        public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library,Identifier resource,boolean looping){
            opened++;
            return CompletableFuture.completedFuture(new MonoStream(pcm,frame,left));
        }
        public void tick(){var p=RailAudioGeometry.position(track,length,listener(),left);x=p.x;y=p.y;z=p.z;}
        @Override public float getVolume(){return volume*(float)Math.min(1,AudioVolumeSettings.gain(false));}
        Vec3 position(){return new Vec3(x,y,z);}
        double cursorTime(){return frame*1000/pcm.rate+(System.nanoTime()-started)/1_000_000.0;}
        void finish(){stop();}
        @Override public boolean canStartSilent(){return true;}
    }
}
