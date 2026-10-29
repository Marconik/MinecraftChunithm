package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;

/** Opt-in checks of actual counters, progress blocks and transport in the isolated test save. */
public final class DevelopmentScoreCheck {
    private static volatile int phase;
    private static volatile boolean acknowledged,finished;
    private static int checks;
    private static int groundChecks;
    private static net.minecraft.world.entity.item.ItemEntity pressureEntity;
    public static int phase(){return phase;}
    public static boolean finished(){return finished;}
    public static void acknowledge(){acknowledged=true;}
    public static String result(){return "{\"passed\":true,\"displayChecks\":"+checks+",\"groundChecks\":"+groundChecks+",\"maximumScore\":1010000,\"notes\":13,\"inPlaceUpdates\":true}";}
    private static void verifyGround(ServerLevel level,Placement head,double time){
        var ground=PartConnections.inputRoute(level,head).ground().part().placement;
        int expected=pressureEntity==null?0:1;
        boolean mirrored=phase>=3;
        if(HeadControlServices.transport(level,head).state()==HeadControlServices.Playback.PLAYING){
            for(int i=1;i<=12;i++)if(time>=i*500&&time<i*500+100)expected|=mirrored?0xc00:0x30;
        }
        var input=HeadControlServices.physicalInput(level,head).ground();
        for(int lane=0;lane<16;lane++)if(input[lane]!=((expected>>lane)&1))
            throw new AssertionError("Native Ground Slide/head lane mismatch at "+time+", phase="+phase+", lane="+lane);
        for(int z=0;z<GroundSlide.DEPTH;z++)for(int x=0;x<GroundSlide.WIDTH;x++){
            int actual=level.getBlockState(ground.block(x,0,z)).getValue(GroundSlideBlock.PRESSED);
            if(actual!=GroundSlidePressure.tileMask(x,expected))throw new AssertionError("Native Ground Slide tile differs at "+x+","+z);
        }
        groundChecks++;
    }
    private static void command(ServerLevel level,String text){
        var server=level.getServer();server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayers().getFirst().createCommandSourceStack(),text);
    }
    private static List<Display> displays(ServerLevel level,Placement p){
        return level.getEntitiesOfClass(Display.class,new AABB(p.point(-.1,0,.9),p.point(11.1,4.1,1.3)),e->e.entityTags().contains("minecraftchunithm.head_display"));
    }
    private static boolean role(Display entity,String field){return entity.entityTags().contains("minecraftchunithm.head_display.value."+field);}
    private static void verify(ServerLevel level,Placement p,HeadDisplayData data,Set<UUID> baseline){
        var entities=displays(level,p);int textCount=0,cells=0;boolean fill=false;
        var ids=new HashSet<UUID>();
        String[] names={"counter.0","counter.1","counter.2","counter.3","combo","score"};
        String[] values={""+data.critical(),"0","0","0",""+data.maxCombo(),String.format(Locale.ROOT,"%,d",data.score())};
        for(var entity:entities){
            if(!role(entity,"fill"))ids.add(entity.getUUID());
            if(entity instanceof Display.TextDisplay text)for(int i=0;i<names.length;i++)if(role(entity,names[i])){
                if(!text.getText().getString().equals(values[i]))throw new AssertionError("Head score text mismatch: "+names[i]+" "+text.getText().getString()+" != "+values[i]);
                textCount++;
            }
            if(entity instanceof Display.BlockDisplay block){
                for(int i=0;i<10;i++)if(role(entity,"cell."+i)){
                    String expected="progress_cell_"+(i<3?"small_":"large_")+(i<data.filledCells()?(i<3?"blue":"gold"):"empty");
                    if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlockState().getBlock()).getPath().equals(expected))
                        throw new AssertionError("Head progress cell diverged at "+i);
                    cells++;
                }
                if(role(entity,"fill")){
                    fill=true;
                    var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());
                    block.saveWithoutId(output);
                    double width=output.buildResult().getCompoundOrEmpty("transformation").getListOrEmpty("scale").getFloatOr(0,Float.NaN);
                    if(!Double.isFinite(width)||Math.abs(width-Math.max(.000001,1008/128.0*data.barFraction()))>.00001)
                        throw new AssertionError("Native progress bar width is not derived from score: "+width);
                    var material=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlockState().getBlock()).getPath();
                    if(!material.equals(data.goldBar()?"yellow_concrete":"light_blue_concrete"))throw new AssertionError("Progress bar color threshold diverged");
                }
            }
        }
        if(textCount!=30||cells!=10||data.barFraction()>0&&!fill)throw new AssertionError("Missing dynamic head displays: "+textCount+" texts, "+cells+" cells");
        if(baseline.isEmpty())baseline.addAll(ids);else if(!baseline.equals(ids))throw new AssertionError("Automatic scoring respawned the facade");
        checks++;
    }
    public static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.scoreQuickPlay"))return;
        int[] ticks={0},wait={0};var baseline=new HashSet<UUID>();
        ServerTickEvents.END_LEVEL_TICK.register(level->{
            if(level!=level.getServer().overworld()||!DevelopmentMotionTest.inputCheckComplete()||finished)return;
            if(++ticks[0]>650)throw new AssertionError("Score test timed out at phase "+phase);
            var player=level.getServer().getPlayerList().getPlayers().getFirst();
            if(phase==0&&player.containerMenu instanceof HeadControlMenu)return;
            var head=DevelopmentMotionTest.inputHead();
            if(phase==0){
                if(Boolean.getBoolean("minecraftchunithm.durabilityQuickPlay"))DevelopmentCabinetCheck.run(level);
                var track=PartConnections.inputRoute(level,head).track().part();
                var notes=new ArrayList<ChartDefinition.Note>();
                for(int i=1;i<=12;i++)notes.add(new ChartDefinition.Note("tap"+i,"TAP",i*500,i*500,4,2,4,2,"",false,false,0,0,"DEF"));
                notes.add(new ChartDefinition.Note("mine","MNE",2000,2000,6,2,6,2,"",false,false,0,0,"DEF"));
                notes.add(new ChartDefinition.Note("decor","ALD",500,6000,8,2,8,2,"",false,false,0,0,"RED"));
                ChartPlayback.load(level,track,new ChartDefinition("All Critical score test",notes,List.of(new ChartDefinition.Speed(1000,2000,-1)),List.of(750.0),List.of(250.0),6000),20,false);
                var camera=head.point(5.5,2,10);command(level,"tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+head.forward().getOpposite().toYRot()+" 0");
                verify(level,head,new HeadDisplayData(0,0,0,0,0,0),baseline);
                var ground=PartConnections.inputRoute(level,head).ground().part().placement;
                var contact=ground.point(2.75,GroundSlide.SURFACE_HEIGHT,1.5);
                pressureEntity=new net.minecraft.world.entity.item.ItemEntity(level,contact.x,contact.y,contact.z,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
                pressureEntity.setNoGravity(true);pressureEntity.setDeltaMovement(0,0,0);pressureEntity.setNeverPickUp();level.addFreshEntity(pressureEntity);
                command(level,"chunithm chart play");phase=1;return;
            }
            var frame=ChartPlayback.nativeFrame(level);if(frame==null)throw new AssertionError("Score session disappeared");
            long passed=Math.max(0,Math.min(12,(long)Math.floor(frame.time()/500)))+(frame.time()>=2000?1:0);
            var score=ChartPlayback.score(level);
            if(score.passed()!=passed||score.total()!=13||score.score()!=passed*1010000/13)throw new AssertionError("Score is not tied to original judgment time");
            verify(level,head,score.display(),baseline);
            verifyGround(level,head,frame.time());
            switch(phase){
                case 1->{if(frame.time()>=3750){command(level,"chunithm chart pause");phase=2;wait[0]=ticks[0]+10;}}
                case 2->{if(ticks[0]>=wait[0]&&acknowledged){
                    if(score.passed()!=8)throw new AssertionError("Pause continued scoring");
                    ChartPlayback.configure(level,head,30,true);
                    if(!ChartPlayback.score(level).equals(score))throw new AssertionError("Speed/mirror changed score");
                    command(level,"chunithm chart seek 1");phase=3;wait[0]=ticks[0]+5;acknowledged=false;
                }}
                case 3->{if(ticks[0]>=wait[0]){if(score.passed()!=2)throw new AssertionError("Backward seek did not recompute score");command(level,"chunithm chart play");phase=4;}}
                case 4->{if(frame.time()>=1550){command(level,"chunithm chart stop");
                    if(ChartPlayback.score(level).score()!=0)throw new AssertionError("Stop did not reset score");
                    verify(level,head,new HeadDisplayData(0,0,0,0,0,0),baseline);command(level,"chunithm chart play");phase=5;
                }}
                case 5->{if(frame.time()>=550){if(score.passed()!=1)throw new AssertionError("Restart duplicated score");
                    command(level,"chunithm chart seek 6");command(level,"chunithm chart pause");
                    verify(level,head,new HeadDisplayData(13,0,0,0,13,1010000),baseline);phase=6;wait[0]=ticks[0]+10;
                }}
                case 6->{if(ticks[0]>=wait[0]&&acknowledged){command(level,"chunithm chart play");phase=7;}}
                case 7->{if(HeadControlServices.transport(level,head).state()==HeadControlServices.Playback.FINISHED){
                    if(HeadControlServices.transport(level,head).state()!=HeadControlServices.Playback.FINISHED)throw new AssertionError("Chart did not finish");
                    pressureEntity.discard();pressureEntity=null;
                    finished=true;MinecraftChunithm.LOGGER.info("AUTO SCORE SMOKE TEST PASSED: {} display checks and {} Ground Slide checks including combined physical pressure, mirrored arrivals, pause/restart and head input routing.",checks,groundChecks);
                }}
            }
        });
    }
}
