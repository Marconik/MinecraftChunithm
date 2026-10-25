package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

/** Opt-in real display checks, confined to the isolated development test save. */
public final class DevelopmentMineCheck {
    private static volatile int phase;
    private static volatile double time=Double.NaN;
    private static volatile boolean ack,finished;
    private static final int[] mineFrames=new int[5];
    public static int phase(){return phase;}
    public static double time(){return time;}
    public static boolean finished(){return finished;}
    public static void acknowledge(){ack=true;}
    private static void command(ServerLevel level,String text){
        var server=level.getServer();server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayers().getFirst().createCommandSourceStack(),text);
    }
    private static void song(ServerLevel level,String difficulty,double speed,boolean mirror){
        try{
            var track=PartConnections.inputRoute(level,DevelopmentMotionTest.inputHead()).track().part();
            var song=SongLibrary.charts(level).stream().filter(s->s.songId()==2999&&s.difficulty().equals(difficulty)).findFirst().orElseThrow();
            ChartPlayback.loadSong(level,track,song,speed,mirror);command(level,"chunithm chart play");
        }catch(java.io.IOException error){throw new RuntimeException(error);}
    }
    public static String result(){return "{\"songId\":2999,\"master\":true,\"ultima\":true,\"mineFramesNormal\":"+mineFrames[3]+",\"mineFramesMirror\":"+mineFrames[4]+",\"offTrackFlight\":false}";}
    public static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.mineQuickPlay"))return;
        int[] ticks={0};
        ServerTickEvents.END_LEVEL_TICK.register(level->{
            if(level!=level.getServer().overworld()||!DevelopmentMotionTest.inputCheckComplete()||finished)return;
            // The input fixture opens a menu whose queue refresh can unload a custom chart.
            // Wait until the client has closed it on the server before loading the fixture.
            if(phase==0&&level.getServer().getPlayerList().getPlayers().getFirst().containerMenu instanceof HeadControlMenu)return;
            if(++ticks[0]>700)throw new AssertionError("Native mine test timed out: phase="+phase+", time="+time);
            if(phase==0){
                var track=PartConnections.inputRoute(level,DevelopmentMotionTest.inputHead()).track().part();
                var camera=track.placement.point(4.5,2.0,track.length+2);
                command(level,"tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+track.placement.forward().getOpposite().toYRot()+" 35");
                level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(camera,camera).inflate(32)).forEach(net.minecraft.world.entity.Entity::discard);
                // An isolated MNE-only chart proves the cue is caused by mines,
                // rather than an overlapping Tap in the real chart.
                var notes=new ArrayList<ChartDefinition.Note>();
                for(double t:new double[]{200,300,400,800,1000})notes.add(new ChartDefinition.Note("mine"+t,"MNE",t,t,4,2,4,2,"",false,false,0,0,"DEF"));
                ChartPlayback.load(level,track,new ChartDefinition("MNE-only cue fixture",notes,List.of(),List.of(),List.of(),2000),3,false);
                command(level,"chunithm chart play");phase=1;
            }
            var frame=ChartPlayback.nativeFrame(level);if(frame==null)return;time=frame.time();
            for(var entry:frame.specs().entrySet())if(entry.getValue().item().startsWith("mine_")){
                var spec=entry.getValue();
                if(!spec.bright()||!frame.positions().containsKey(entry.getKey()))throw new AssertionError("Mine must be a real fullbright display");
                if(spec.x()<.5||spec.x()>8.5||spec.y()!=.025)throw new AssertionError("Mine escaped the track surface");
                mineFrames[phase]++;
            }
            if(ack&&time>=(phase==1?1200:phase==2?3200:phase==3?3600:3300)){
                int next=phase+1;time=Double.NaN;ack=false;
                if(next==2)song(level,"MASTER",20,false);
                else if(next==3)song(level,"ULTIMA",3,false);
                else if(next==4)song(level,"ULTIMA",20,true);
                else{
                    if(mineFrames[1]==0||mineFrames[3]==0||mineFrames[4]==0)throw new AssertionError("Missing native MNE displays");
                    command(level,"chunithm chart pause");finished=true;
                    MinecraftChunithm.LOGGER.info("MELODiniq MINE SMOKE TEST PASSED: MASTER, ULTIMA, mirrored emissive mines and isolated mine-only cues.");
                }
                phase=next;
            }
        });
    }
}
