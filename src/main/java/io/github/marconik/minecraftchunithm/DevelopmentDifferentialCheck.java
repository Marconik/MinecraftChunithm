package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

/** Opt-in inspection of real server-side display entities in the isolated native client test. */
public final class DevelopmentDifferentialCheck {
    private static volatile boolean waiting,finished,audioAck;
    private static volatile double time=Double.NaN;
    private static volatile int pass;
    private static final long[][] counts=new long[2][3];
    private record Seen(double z,int profile){}
    public static double time(){return time;}
    public static int pass(){return pass;}
    public static boolean waiting(){return waiting;}
    public static boolean finished(){return finished;}
    public static void acknowledgeAudio(){audioAck=true;}
    public static String result(){
        var json=new com.google.gson.JsonObject();json.addProperty("songId",2969);json.addProperty("difficulty","MASTER");json.addProperty("speed",20);
        for(int i=0;i<2;i++){
            var p=new com.google.gson.JsonObject();p.addProperty("defaultRatePairs",counts[i][0]);p.addProperty("alternateRatePairs",counts[i][1]);
            p.addProperty("simultaneousDifferentialFrames",counts[i][2]);json.add(i==0?"normal":"mirror",p);
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json);
    }
    private static void command(ServerLevel level,String command){
        var server=level.getServer();server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayers().getFirst().createCommandSourceStack(),command);
    }
    private static void start(ServerLevel level){
        command(level,"chunithm chart play 2969 20 "+(pass==1));command(level,"chunithm chart seek 122.65");
    }
    public static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.differentialQuickPlay"))return;
        int[] phase={0},ticks={0};long[] pauseAt={0};double[] previousTime={Double.NaN};Map<String,Seen> previous=new HashMap<>();
        ServerTickEvents.END_LEVEL_TICK.register(level->{
            if(level!=level.getServer().overworld()||!DevelopmentMotionTest.inputCheckComplete()||finished)return;
            if(++ticks[0]>600)throw new AssertionError("Native differential test timed out");
            if(phase[0]==0){
                var player=level.getServer().getPlayerList().getPlayers().getFirst();
                var track=PartConnections.inputRoute(level,DevelopmentMotionTest.inputHead()).track().part();
                if(SongLibrary.charts(level).stream().noneMatch(s->s.songId()==2969&&s.difficulty().equals("MASTER")))throw new AssertionError("mu3 MASTER is not selectable");
                var camera=track.placement.point(4.5,1,track.length+2);
                command(level,"tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+track.placement.forward().getOpposite().toYRot()+" 25");
                start(level);phase[0]=1;
            }
            var frame=ChartPlayback.nativeFrame(level);if(frame==null)return;time=frame.time();
            if(phase[0]==1){
                boolean window=time>=122750&&time<=123050||time>=124550&&time<=124950||time>=126450&&time<=126850||time>=128350&&time<=128700;
                boolean beforeWindow=previousTime[0]>=122750&&previousTime[0]<=123050||previousTime[0]>=124550&&previousTime[0]<=124950
                        ||previousTime[0]>=126450&&previousTime[0]<=126850||previousTime[0]>=128350&&previousTime[0]<=128700;
                int[] observed={0,0};Map<String,Seen> current=new HashMap<>();
                for(var entry:frame.specs().entrySet()){
                    var spec=entry.getValue();
                    if(spec.item().endsWith("_body")||spec.item().equals("beat_line")||spec.item().equals("speed_change_line"))continue;
                    var point=frame.positions().get(entry.getKey());var delta=point.subtract(frame.track().point(0,0,0));
                    double z=delta.x*frame.track().forward().getStepX()+delta.z*frame.track().forward().getStepZ();
                    if(Math.abs(z-spec.z())>1e-5)throw new AssertionError("Actual display position differs from its rendered profile geometry");
                    if(z<=.25||z>=3.25)continue;
                    current.put(entry.getKey(),new Seen(z,spec.profile()));var old=previous.get(entry.getKey());
                    if(old==null||!window||!beforeWindow||time-previousTime[0]!=50)continue;
                    double expected=spec.profile()==0?.5:time<123125?.45:time<125000?.4:time<126875?.35:.3;
                    if(Math.abs(z-old.z()-expected)>1e-6)throw new AssertionError("Actual note did not follow its independent speed: "+spec.profile()+" delta="+(z-old.z())+" expected="+expected);
                    observed[spec.profile()]++;counts[pass][spec.profile()]++;
                }
                if(observed[0]>0&&observed[1]>0)counts[pass][2]++;
                previous.clear();previous.putAll(current);previousTime[0]=time;
                if(time>=129000){
                    if(counts[pass][0]==0||counts[pass][1]==0||counts[pass][2]==0)throw new AssertionError("Did not observe simultaneous native note speeds");
                    MinecraftChunithm.LOGGER.info("mu3 native differential passed: mirror={}, default pairs={}, alternate pairs={}, simultaneous frames={}",pass==1,counts[pass][0],counts[pass][1],counts[pass][2]);
                    command(level,"chunithm chart pause");audioAck=false;waiting=true;pauseAt[0]=level.getGameTime();phase[0]=2;
                }
            }else if(phase[0]==2&&level.getGameTime()-pauseAt[0]>=10&&audioAck){
                if(pass==0){time=Double.NaN;pass=1;previous.clear();previousTime[0]=Double.NaN;start(level);phase[0]=1;waiting=false;}
                else {waiting=false;finished=true;MinecraftChunithm.LOGGER.info("MU3 DIFFERENTIAL SMOKE TEST PASSED: actual displays move at separate rates simultaneously in both mirrors.");}
            }
        });
    }
}
