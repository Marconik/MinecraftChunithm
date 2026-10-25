package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.util.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Display;

/** Observes actual client display movement while the imported MASTER and its music play. */
final class DevelopmentReverseCapture {
    private static void command(Minecraft client,String command){
        var server=client.getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(
                server.getPlayerList().getPlayers().getFirst().createCommandSourceStack(),command));
    }
    private static void start(Minecraft client,boolean mirror){
        command(client,"chunithm chart play 2579 20 "+mirror);
        command(client,"chunithm chart seek 52.8");
    }
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.reverseQuickPlay"))return;
        int[] phase={0},ticks={0},wait={0},pass={0};long[] backwards={0,0},forwards={0,0};
        double[] previousTime={Double.NaN};boolean[][] captures=new boolean[2][3];
        Map<UUID,Double> previous=new HashMap<>();
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!DevelopmentMotionTest.inputCheckComplete()||client.player==null)return;
            if(++ticks[0]>600)throw new AssertionError("Reverse native check timed out in phase "+phase[0]);
            double time=NoteCuePlayback.checkTime();
            if(phase[0]==0){
                client.player.closeContainer();var server=client.getSingleplayerServer();
                server.execute(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();var head=DevelopmentMotionTest.inputHead();
                    var track=PartConnections.inputRoute(player.level(),head).track().part();
                    if(SongLibrary.charts(player.level()).stream().noneMatch(s->s.songId()==2579&&s.difficulty().equals("MASTER")))
                        throw new AssertionError("Imported MASTER is missing from the playable song catalog");
                    var camera=track.placement.point(4.5,1,track.length+2);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+track.placement.forward().getOpposite().toYRot()+" 25");
                });
                start(client,false);phase[0]=1;return;
            }
            if(phase[0]==1){
                if(time>=52750&&time<53100){phase[0]=2;previous.clear();previousTime[0]=Double.NaN;}
                return;
            }
            if(phase[0]==2){
                var track=PartConnections.target(DevelopmentMotionTest.inputHead(),PartConnections.Kind.TRACK);
                Map<UUID,Double> current=new HashMap<>();
                for(var entity:client.level.entitiesForRendering())if(entity instanceof Display.ItemDisplay display){
                    var id=BuiltInRegistries.ITEM.getKey(display.getItemStack().getItem());var name=id.getPath();
                    if(!id.getNamespace().equals(MinecraftChunithm.MOD_ID)||!(name.startsWith("tap_")||name.startsWith("ex_tap_")||name.startsWith("flick_")||name.startsWith("chart_slide_node_")||name.startsWith("chart_air_arrow_")))continue;
                    var delta=entity.position().subtract(track.point(0,0,0));
                    double z=delta.x*track.forward().getStepX()+delta.z*track.forward().getStepZ();
                    if(z>.25&&z<3.75)current.put(entity.getUUID(),z);
                }
                boolean reverseWindow=time>=53300&&time<=53900&&previousTime[0]>=53300
                        ||time>=54100&&time<=54700&&previousTime[0]>=54100;
                boolean forwardWindow=time>=56000&&previousTime[0]>=56000;
                if(time>previousTime[0]&&time-previousTime[0]<120)for(var e:current.entrySet()){
                    Double old=previous.get(e.getKey());if(old==null)continue;
                    double motion=e.getValue()-old;
                    if(reverseWindow&&motion< -1e-5)backwards[pass[0]]++;
                    if(forwardWindow&&motion>1e-5)forwards[pass[0]]++;
                }
                previous.clear();previous.putAll(current);previousTime[0]=time;
                double[] at={53600,55300,56200};
                for(int i=0;i<at.length;i++)if(time>=at[i]&&!captures[pass[0]][i]){
                    captures[pass[0]][i]=true;
                    Screenshot.grab(client.gameDirectory,"last-piece-"+(pass[0]==0?"normal":"mirror")+"-"+i+".png",client.gameRenderer.mainRenderTarget(),1,
                            message->MinecraftChunithm.LOGGER.info("Reverse playback capture: {}",message.getString()));
                }
                if(time>=56600){
                    if(backwards[pass[0]]==0||forwards[pass[0]]==0)throw new AssertionError("Actual note entities did not reverse and resume: "+backwards[pass[0]]+" / "+forwards[pass[0]]);
                    RailMusicPlayback.verifyNative(true,56000);
                    MinecraftChunithm.LOGGER.info("Last Piece native movement passed: mirror={}, backward pairs={}, forward pairs={}",pass[0]==1,backwards[pass[0]],forwards[pass[0]]);
                    command(client,"chunithm chart pause");wait[0]=ticks[0]+10;phase[0]=3;
                }
                return;
            }
            if(phase[0]==3&&ticks[0]>=wait[0]){
                RailMusicPlayback.verifyNative(false,0);NoteCuePlayback.verifyNative(false);
                if(pass[0]++==0){start(client,true);phase[0]=1;return;}
                try{
                    var result=new com.google.gson.JsonObject();
                    result.addProperty("songId",2579);result.addProperty("difficulty","MASTER");result.addProperty("speed",20);
                    result.addProperty("normalBackwardPairs",backwards[0]);result.addProperty("normalForwardPairs",forwards[0]);
                    result.addProperty("mirrorBackwardPairs",backwards[1]);result.addProperty("mirrorForwardPairs",forwards[1]);
                    result.addProperty("railMusicAndPauseVerified",true);
                    Files.writeString(client.gameDirectory.toPath().resolve("last-piece-reverse-checks.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
                }catch(java.io.IOException error){throw new RuntimeException(error);}
                MinecraftChunithm.LOGGER.info("LAST PIECE REVERSE SMOKE TEST PASSED: actual note entities reverse/resume in both mirrors; original stereo music and pause remain correct.");
                client.stop();phase[0]=4;
            }
        });
    }
}
