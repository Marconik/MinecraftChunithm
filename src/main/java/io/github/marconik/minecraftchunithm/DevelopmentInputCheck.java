package io.github.marconik.minecraftchunithm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Explicit development test only; run in a disposable copy of the test world. */
public final class DevelopmentInputCheck {
    public static void showDecorativeLines(CommandSourceStack source,int songId){
        var level=source.getLevel();var track=PartConnections.inputRoute(level,DevelopmentMotionTest.inputHead()).track().part();
        if(songId>0){
            var chart=SongLibrary.charts(level).stream().filter(song->song.songId()==songId&&song.difficulty().equals(songId==2967?"ULTIMA":"MASTER")).findFirst().orElseThrow();
            try{ChartPlayback.loadSong(level,track,chart,1.5,false);}catch(java.io.IOException error){throw new IllegalStateException(error);}
            level.getServer().getCommands().performPrefixedCommand(source,"chunithm chart seek "+(songId==2967?145:110));
            return;
        }
        var colors=java.util.List.of("GRY","AQA","RED","BLK","DEF","DGR","GRN","PNK","CYN","BLU","ORN","YEL","PPL");
        var notes=new java.util.ArrayList<ChartDefinition.Note>();
        for(int i=0;i<colors.size();i++)notes.add(new ChartDefinition.Note("color_"+i,"ALD",500,2000,i,1,i,1,"",false,false,5,5,colors.get(i)));
        notes.add(new ChartDefinition.Note("air_reference","AHD",500,2000,14,1,14,1,"AIR",true,true,5,5,"DEF"));
        ChartPlayback.load(level,track,new ChartDefinition("ALD/Air same-width palette",notes,java.util.List.of(),java.util.List.of(),java.util.List.of(),2000),1.5,false);
        level.getServer().getCommands().performPrefixedCommand(source,"chunithm chart seek 0");
    }
    public static void showYounithm(CommandSourceStack source,String difficulty){
        showSongDifficulties(source,2967,difficulty);
    }
    public static void showSongDifficulties(CommandSourceStack source,int songId,String difficulty){
        var level=source.getLevel();var head=PartConnections.find(level,PartConnections.Kind.HEAD,DevelopmentMotionTest.inputHead());
        var panel=PartConnections.find(level,PartConnections.Kind.SONG,PartConnections.target(head.placement,PartConnections.Kind.SONG));
        ChartPlayback.unload(level);
        MusicQueue.setPanelInputs(level,panel,SongQueueData.empty(),MusicQueueData.DEFAULT_SPEED,false);
        MusicQueue.setPanelChoice(level,panel,0,new MusicQueueData.Choice(songId,difficulty,false));
        MusicQueue.setPanelChoice(level,panel,1,new MusicQueueData.Choice(songId,difficulty.equals("MASTER")?"ULTIMA":"MASTER",false));
        MusicQueue.prepare(level,head);
        require(panel.song.difficulty().name().equals(difficulty),"YOUNITHM panel difficulty did not follow selection");
        var catalog=SongLibrary.catalog(level).stream().filter(song->song.songId()==songId).findFirst().orElseThrow();
        require(catalog.difficulties().stream().filter(d->d.available()&&(d.name().equals("MASTER")||d.name().equals("ULTIMA"))).count()==2,
                "Both YOUNITHM difficulties must be selectable");
        var p=panel.placement;
        long backgrounds=level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
                new net.minecraft.world.phys.AABB(p.point(-.1,-.1,.9),p.point(7.1,3.1,1.3)),
                entity->net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).getPath().equals("song_ultima_background")).size();
        require(backgrounds==(difficulty.equals("ULTIMA")?1:0),"Stale or missing ULTIMA panel background");
        require(panel.song.speed()==20&&panel.music.speed()==20,"Default speed did not reach the physical panel");
        MinecraftChunithm.LOGGER.info("Song {} native selection verified: {}, title={}, level={}, speed={}, patterned layers={}",songId,difficulty,panel.song.title(),panel.song.level(),panel.song.speed(),backgrounds);
    }
    public static void openYounithmPanel(CommandSourceStack source){
        var owner=(net.minecraft.server.level.ServerPlayer)source.getEntity();
        var panel=PartConnections.find(source.getLevel(),PartConnections.Kind.SONG,
                PartConnections.target(DevelopmentMotionTest.inputHead(),PartConnections.Kind.SONG));
        PartWindows.open(owner,source.getLevel(),panel,0);
    }
    public static void showAirActions(CommandSourceStack source){
        var level=source.getLevel();var track=PartConnections.inputRoute(level,DevelopmentMotionTest.inputHead()).track().part();
        var notes=java.util.List.of(
                new ChartDefinition.Note("held","AHD",500,2000,2,2,2,2,"AIR",true,true,0,0,"DEF"),
                new ChartDefinition.Note("slant","ASC",500,2000,7,2,12,2,"AIR",true,true,2,8,"DEF"),
                new ChartDefinition.Note("down","ADW",2000,2000,12,2,12,2,"",false,false,0,0,"DEF"),
                new ChartDefinition.Note("deck","TAP",1000,1000,4,4,4,4,"",false,false,0,0,"DEF"));
        var chart=new ChartDefinition("Air Action height/glow check",notes,java.util.List.of(),java.util.List.of(),java.util.List.of(),2000);
        ChartPlayback.load(level,track,chart,1.5,false);
        level.getServer().getCommands().performPrefixedCommand(source,"chunithm chart seek 0");
    }
    private DevelopmentInputCheck(){}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static Placement run(CommandSourceStack source){
        var level=source.getLevel();int index=0;Placement lastHead=null;
        for(var direction:Direction.Plane.HORIZONTAL){
            int x=4096+48*index++;
            for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=4080>>4;cz<=4112>>4;cz++)level.getChunk(cx,cz);
            var anchor=source.withPosition(new Vec3(x+.5,-60,4096.5)).withRotation(new Vec2(0,direction.toYRot()));
            require(PartAssembly.generate(anchor,4)==1,"Input test assembly failed");
            var head=Placement.head(anchor);
            lastHead=head;
            var route=PartConnections.inputRoute(level,head);
            require(route.track().connected()&&route.ground().connected()&&route.leftAir().connected()&&route.rightAir().connected(),
                    "Every input hop must be connected in "+direction);
            for(var sensor:new PartConnections.Part[]{route.leftAir().part(),route.rightAir().part()}){
                int wall=sensor.kind==PartConnections.Kind.AIR_LEFT?0:AirSensors.BASE_WIDTH-1;
                var top=sensor.placement.block(wall,AirSensors.PANEL_HEIGHT,0);
                var saved=level.getBlockState(top);
                for(int z=0;z<AirSensors.DEPTH;z++)level.setBlock(sensor.placement.block(wall,AirSensors.PANEL_HEIGHT,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
                // A single occupied cell must prevent a partial or destructive extension.
                level.setBlock(top,saved,Block.UPDATE_CLIENTS);
                AirSensors.upgradeLegacy(level,sensor);
                require(level.getBlockState(sensor.placement.block(wall,AirSensors.PANEL_HEIGHT,1)).isAir(),"Obstructed AIR extension changed other top cells");
                level.setBlock(top,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
                AirSensors.upgradeLegacy(level,sensor);
                require(PartConnections.integrity(level,sensor)==PartConnections.Status.CONNECTED,"Legacy AIR extension did not restore the full structure");
            }
            var ground=route.ground().part().placement;
            AirSensors.setInputProvider((l,p,left)->new AirSensors.Signal(left?4:8));
            try{
                var input=HeadControlServices.physicalInput(level,head);
                require(input.leftAir()==.6&&input.rightAir()==.8,"AIR signals did not reach the head independently");
                // Exercise the real half-block cabinet state, including both outer half-lanes.
                for(int lane=0;lane<16;lane++){
                    int half=lane+5;var pos=ground.block(half/2,0,1);var saved=level.getBlockState(pos);
                    try{
                        level.setBlock(pos,saved.setValue(GroundSlideBlock.PRESSED,1<<(half%2)),Block.UPDATE_CLIENTS);
                        var responses=HeadControlServices.physicalInput(level,head).ground();
                        for(int other=0;other<16;other++)require(responses[other]==(other==lane?1:0),"Ground lane routing mismatch");
                    }finally{level.setBlock(pos,saved,Block.UPDATE_CLIENTS);}
                }
                for(var kind:new PartConnections.Kind[]{PartConnections.Kind.AIR_LEFT,PartConnections.Kind.AIR_RIGHT,
                        PartConnections.Kind.GROUND_SLIDE,PartConnections.Kind.TRACK,PartConnections.Kind.HEAD}){
                    var part=switch(kind){
                        case AIR_LEFT->route.leftAir().part();case AIR_RIGHT->route.rightAir().part();
                        case GROUND_SLIDE->route.ground().part();case TRACK->route.track().part();
                        default->PartConnections.find(level,kind,head);
                    };
                    var pos=part.placement.block(0,0,0);var saved=level.getBlockState(pos);
                    try{
                        level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
                        var disconnected=HeadControlServices.physicalInput(level,head);
                        require(disconnected.leftAirConnected()==(kind==PartConnections.Kind.AIR_RIGHT),"Left AIR did not follow upstream failure");
                        require(disconnected.rightAirConnected()==(kind==PartConnections.Kind.AIR_LEFT),"Right AIR did not follow upstream failure");
                        require(disconnected.groundConnected()==(kind==PartConnections.Kind.AIR_LEFT||kind==PartConnections.Kind.AIR_RIGHT),
                                "AIR failure must not disconnect Ground Slide");
                        require(disconnected.leftAir()==(disconnected.leftAirConnected()?.6:0)
                                &&disconnected.rightAir()==(disconnected.rightAirConnected()?.8:0),"Broken route leaked AIR signals");
                        level.setBlock(pos,saved.setValue(HeadTile.FACING,part.placement.forward().getClockWise()),Block.UPDATE_CLIENTS);
                        var rotated=HeadControlServices.physicalInput(level,head);
                        require(rotated.leftAirConnected()==disconnected.leftAirConnected()
                                &&rotated.rightAirConnected()==disconnected.rightAirConnected(),"Wrong facing must break the same input path");
                    }finally{level.setBlock(pos,saved,Block.UPDATE_CLIENTS);}
                    require(HeadControlServices.physicalInput(level,head).leftAirConnected()
                            &&HeadControlServices.physicalInput(level,head).rightAirConnected(),"Restored input path did not reconnect");
                }
                // A colocated sensor registered with the wrong orientation must not be accepted.
                var left=route.leftAir().part();
                try{
                    PartConnections.register(level,PartConnections.Kind.AIR_LEFT,
                            new Placement(left.placement.origin(),left.placement.forward().getOpposite()),1);
                    require(!HeadControlServices.physicalInput(level,head).leftAirConnected(),"Misaligned AIR registry was accepted");
                }finally{PartConnections.register(level,PartConnections.Kind.AIR_LEFT,left.placement,1);}
            }finally{AirSensors.resetInputProvider();}
            // Physical detection and release: entities on one side must not activate the other.
            var baseline=HeadControlServices.physicalInput(level,head);
            var entity=EntityTypes.ARMOR_STAND.create(level,EntitySpawnReason.TRIGGERED);
            require(entity!=null,"Could not create physical AIR test entity");
            var point=route.leftAir().part().placement.point(2.5,1,1.5);
            entity.setPos(point.x,point.y,point.z);level.addFreshEntity(entity);
            try{
                var input=HeadControlServices.physicalInput(level,head);
                require(input.leftAir()>0&&input.rightAir()==baseline.rightAir(),"Physical AIR response is not isolated by side");
            }finally{entity.discard();}
            var released=HeadControlServices.physicalInput(level,head);
            require(released.leftAir()==baseline.leftAir()&&released.rightAir()==baseline.rightAir(),"AIR response did not return to baseline after release");
            var player=source.getEntity();
            if(player instanceof net.minecraft.server.level.ServerPlayer owner){
                var menu=new HeadControlMenu(0,owner.getInventory(),level,PartConnections.find(level,PartConnections.Kind.HEAD,head),owner);
                require(menu.connected(HeadControlMenu.AIR_LEFT)&&menu.connected(HeadControlMenu.AIR_RIGHT)
                        &&menu.connected(HeadControlMenu.GROUND),"Native menu did not publish physical input connectivity");
                if(index==4){
                    TrackHeadDisplay.update(level,head,new HeadDisplayData(20,3,0,1,240,455_000));
                    var camera=head.point(5.5,1,-3);
                    level.getServer().getCommands().performPrefixedCommand(source,
                            "tp @s "+camera.x+" "+camera.y+" "+camera.z+" "+head.forward().toYRot()+" 5");
                    PartWindows.open(owner,level,PartConnections.find(level,PartConnections.Kind.HEAD,head),0);
                }
            }
        }
        MinecraftChunithm.LOGGER.info("AIR input pipeline checks passed: four facings; independent height channels; 16 Ground lanes; broken, rotated and restored hops; physical detection/release; native menu connectivity.");
        return lastHead;
    }
}
