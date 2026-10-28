package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Actual imported displays, strictly confined to the disposable integration save. */
public final class DevelopmentOffTrackCheck {
    private static volatile String result;
    private static volatile int phase;
    private static volatile boolean capture;
    private static volatile boolean ready,cameraReady;
    private static volatile Vec3 camera;
    public static String result(){return result;}
    public static int phase(){return phase;}
    public static boolean capture(){return capture;}
    public static void acknowledge(){capture=false;}
    public static void ready(){ready=true;}
    public static Vec3 camera(){return camera;}
    public static void acknowledgeCamera(){cameraReady=true;}
    private DevelopmentOffTrackCheck(){}
    public static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.offTrackQuickPlay"))return;
        int[] ticks={0},age={0};long[][] observed=new long[2][4];
        PartConnections.Part[] track={null};ChartDefinition[] chart={null};
        var sourceNotes=new HashMap<String,ChartDefinition.Note>();
        double[] times={8000,50000,97050,98800,123650,142250,148000,165250,170200,174200};
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(result!=null||!ready||server.getPlayerList().getPlayers().isEmpty())return;
            if(++ticks[0]>800)throw new AssertionError("Off-track native test timed out at phase "+phase);
            var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
            if(ticks[0]<20)return;
            if(track[0]==null){
                String path=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString().replace('\\','/');
                if(!path.contains("/build/air-input-test/"))throw new AssertionError("Refusing off-track test outside isolated save");
                if(player.isDeadOrDying()||player.isRemoved())player=server.getPlayerList().respawn(player,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
                var source=player.createCommandSourceStack().withPosition(new Vec3(12288.5,-50,4096.5)).withRotation(new Vec2(0,180));
                var head=Placement.head(source);
                for(int x=-16;x<=32;x+=8)for(int z=-8;z<=40;z+=8){var p=ChunkPos.containing(head.block(x,0,z));level.getChunk(p.x(),p.z());}
                if(PartAssembly.generate(source,24)!=1)throw new AssertionError("Off-track test cabinet generation failed");
                track[0]=PartConnections.find(level,PartConnections.Kind.TRACK,PartConnections.target(head,PartConnections.Kind.TRACK));
                try{chart[0]=ChartDefinition.load(level,"minecraftchunithm:songs/2999/ultima.json");}catch(java.io.IOException e){throw new RuntimeException(e);}
                for(var note:chart[0].notes())sourceNotes.put(note.id(),note);
                camera=track[0].placement.point(4.5,10,32);
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+track[0].placement.forward().getOpposite().toYRot()+" 38");
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"time set day");
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"weather clear");
            }
            if(!cameraReady)return;
            if(age[0]==0){
                for(int x=0;x<9;x+=4)for(int z=0;z<24;z+=8){var p=ChunkPos.containing(track[0].placement.block(x,0,z));level.getChunk(p.x(),p.z());}
                if(PartConnections.integrity(level,track[0])!=PartConnections.Status.CONNECTED)
                    throw new AssertionError("Native test track lost integrity: "+PartConnections.integrity(level,track[0]));
                ChartPlayback.load(level,track[0],chart[0],20,phase>=times.length);
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"chunithm chart seek "+times[phase%times.length]/1000);
                ChartPlayback.control(player.createCommandSourceStack(),HeadControlServices.Action.START_OR_RESUME);
            }
            if(++age[0]==8){
                var frame=ChartPlayback.nativeFrame(level);if(frame==null||frame.specs().isEmpty())throw new AssertionError("Native chart failed to render");
                var expected=ChartPlayback.checkFrame(chart[0],20,phase>=times.length,frame.time());
                int pass=phase/times.length;
                for(var entry:frame.specs().entrySet()){
                    var spec=entry.getValue();var reference=expected.get(entry.getKey());var world=frame.positions().get(entry.getKey());
                    if(!spec.equals(reference)||world.distanceTo(frame.track().point(spec.x(),TrackBlock.SURFACE_HEIGHT+spec.y(),spec.z()))>1e-5)
                        throw new AssertionError("Actual off-track entity differs from source geometry: "+entry.getKey());
                    if(spec.item().equals("beat_line")||spec.item().equals("speed_change_line"))continue;
                    if(spec.x()<.5||spec.x()>8.5)observed[pass][0]++;
                    if(spec.item().endsWith("_body")&&Math.abs(spec.shear())>.01)observed[pass][1]++;
                    if(spec.item().equals("chart_air_body")&&(spec.y()!=1||spec.lift()!=0))observed[pass][2]++;
                    var note=sourceNotes.get(entry.getKey());
                    if(note!=null&&Set.of("TAP","CHR","MNE","FLK","AIR","AUL","AUR","ADW","ADL","ADR").contains(note.type())){
                        double outside=Math.max(0,Math.max(-note.lane(),note.lane()+note.width()-16))*.5;
                        double raised=Math.min(1,outside)*Math.clamp(spec.z()/(track[0].length-.5),0,1);
                        if(Math.abs(spec.y()-(.025+raised))>1e-7)throw new AssertionError("Native cap is not on the raised outside deck");
                        if(raised>0)observed[pass][3]++;
                    }
                }
                ChartPlayback.control(player.createCommandSourceStack(),HeadControlServices.Action.PAUSE);
                capture=true;
            }
            if(age[0]<8||capture)return;
            if(++phase==times.length*2){
                for(var counts:observed)for(long count:counts)if(count==0)throw new AssertionError("Missing native off-track observations");
                ChartPlayback.control(player.createCommandSourceStack(),HeadControlServices.Action.PAUSE);
                result="{\"passed\":true,\"songId\":2999,\"difficulty\":\"ULTIMA\",\"normal\":"+Arrays.toString(observed[0])+",\"mirror\":"+Arrays.toString(observed[1])+",\"samples\":"+(times.length*2)+"}";
                MinecraftChunithm.LOGGER.info("MELODiniq OFF-TRACK NATIVE PASSED: {}",result);
            }else age[0]=0;
        });
    }
}
