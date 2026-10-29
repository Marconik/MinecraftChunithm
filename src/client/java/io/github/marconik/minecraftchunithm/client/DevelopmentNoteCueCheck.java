package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Opt-in native-engine checks in the isolated AIR test save, never in a user's normal world. */
final class DevelopmentNoteCueCheck {
    private static ChartDefinition.Note note(String type,double start,double end,boolean root,boolean terminal){
        return new ChartDefinition.Note(type+start,type,start,end,4,2,4,2,"",root,terminal,5,5,type.equals("ALD")?"RED":"DEF");
    }
    private static void command(Minecraft client,String command){
        var server=client.getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(
                server.getPlayerList().getPlayers().getFirst().createCommandSourceStack(),"chunithm chart "+command));
    }
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.noteCueQuickPlay"))return;
        int[] phase={0},ticks={0},wait={0};long[] previous={0};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!DevelopmentMotionTest.inputCheckComplete()||client.player==null)return;
            if(++ticks[0]>600)throw new AssertionError("Native note cue check timed out in phase "+phase[0]);
            double time=NoteCuePlayback.checkTime();
            switch(phase[0]){
                case 0->{
                    client.player.closeContainer();
                    var server=client.getSingleplayerServer();server.execute(()->{
                        var player=server.getPlayerList().getPlayers().getFirst();
                        var track=PartConnections.inputRoute(player.level(),DevelopmentMotionTest.inputHead()).track().part();
                        var point=track.placement.point(4.5,1,track.length+1);
                        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"tp @s "+point.x+" "+point.y+" "+point.z);
                        var notes=new ArrayList<ChartDefinition.Note>();
                        notes.add(note("TAP",0,0,false,false));notes.add(note("CHR",0,0,false,false));
                        notes.add(note("HLD",500,700,true,true));
                        notes.add(note("SLD",1000,1200,true,false));notes.add(note("SLC",1200,1400,false,true));
                        notes.add(note("AIR",1600,1600,false,false));notes.add(note("FLK",1800,1800,false,false));
                        notes.add(note("HXD",2000,2200,true,true));
                        for(int i=0;i<4;i++)notes.add(note("TAP",2500+i*50,2500+i*50,false,false));
                        notes.add(note("ALD",2700,2900,true,true));notes.add(note("TAP",3500,3500,false,false));
                        ChartPlayback.load(player.level(),track,new ChartDefinition("Native cue check",notes,List.of(),List.of(2800.0),List.of(2750.0),5000),20,false);
                        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"chunithm chart play");
                    });phase[0]=1;
                }
                case 1->{if(time>=80&&time<240){NoteCuePlayback.verifyNative(true);phase[0]=2;}}
                case 2->{if(time>=2700){
                    if(NoteCuePlayback.checkTriggers()<10||NoteCuePlayback.checkReplacements()<3)throw new AssertionError("Dense notes did not interrupt previous cues");
                    command(client,"pause");wait[0]=ticks[0]+10;phase[0]=3;
                }}
                case 3->{if(ticks[0]>=wait[0]){NoteCuePlayback.verifyNative(false);previous[0]=NoteCuePlayback.checkTriggers();command(client,"seek 3");wait[0]=ticks[0]+10;phase[0]=4;}}
                case 4->{if(ticks[0]>=wait[0]){
                    NoteCuePlayback.verifyNative(false);
                    if(NoteCuePlayback.checkTriggers()!=previous[0])throw new AssertionError("Pause/seek replayed old cues");
                    command(client,"play");phase[0]=5;
                }}
                case 5->{if(time>=3580){
                    NoteCuePlayback.verifyNative(true);
                    if(NoteCuePlayback.checkTriggers()!=previous[0]+1)throw new AssertionError("Resume/seek did not play exactly the next note");
                    command(client,"stop");wait[0]=ticks[0]+10;phase[0]=6;
                }}
                case 6->{if(ticks[0]>=wait[0]){NoteCuePlayback.verifyNative(false);previous[0]=NoteCuePlayback.checkTriggers();command(client,"play");phase[0]=7;}}
                case 7->{if(time>=80&&time<240){
                    NoteCuePlayback.verifyNative(true);
                    if(NoteCuePlayback.checkTriggers()!=previous[0]+1)throw new AssertionError("Restart lost or repeated the first chord");
                    var server=client.getSingleplayerServer();server.execute(()->ChartPlayback.unload(server.overworld()));
                    wait[0]=ticks[0]+10;phase[0]=8;
                }}
                case 8->{if(ticks[0]>=wait[0]){NoteCuePlayback.verifyNative(false);command(client,"play 103 20 false");wait[0]=ticks[0]+30;phase[0]=9;}}
                case 9->{if(ticks[0]>=wait[0]){command(client,"seek 60");wait[0]=ticks[0]+20;phase[0]=10;}}
                case 10->{if(ticks[0]>=wait[0]){
                    RailMusicPlayback.verifyNative(true,60000);
                    if(NoteCuePlayback.checkTriggers()<=previous[0]+1)throw new AssertionError("Note cue transport must coexist with real song audio");
                    command(client,"pause");wait[0]=ticks[0]+10;phase[0]=11;
                }}
                case 11->{if(ticks[0]>=wait[0]){
                    NoteCuePlayback.verifyNative(false);RailMusicPlayback.verifyNative(false,0);
                    MinecraftChunithm.LOGGER.info("NOTE CUE SMOKE TEST PASSED: native paired sensor streams, dense replacement, pause, seek, resume, restart, unload, and coexistence with ID103 rail music.");
                    client.stop();phase[0]=12;
                }}
                default->{}
            }
        });
    }
}
