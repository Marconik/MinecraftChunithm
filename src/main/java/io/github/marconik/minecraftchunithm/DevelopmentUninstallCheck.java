package io.github.marconik.minecraftchunithm;

import java.nio.file.Files;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Destructive integration test confined to the opt-in build/air-input-test save. */
public final class DevelopmentUninstallCheck {
    private static volatile String result;
    public static String result(){return result;}
    private DevelopmentUninstallCheck(){}
    public static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.uninstallQuickPlay"))return;
        int[] phase={0},ticks={0};var markers=new HashMap<ServerLevel,BlockPos>();
        BlockPos remote=new BlockPos(-8192,-50,-8208);ChunkPos remoteChunk=ChunkPos.containing(remote);
        UUID[] control={null};long[] removed={0};
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(result!=null||server.getPlayerList().getPlayers().isEmpty())return;
            if(++ticks[0]>12000)throw new AssertionError("Uninstall integration timed out at phase "+phase[0]);
            ServerLevel level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
            if(phase[0]==0){
                PathCheck.requireIsolated(server.getWorldPath(LevelResource.ROOT));
                if(player.isDeadOrDying()||player.isRemoved())player=server.getPlayerList().respawn(player,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"tp @s 4249 -50 4096.5 90 0");
                var source=player.createCommandSourceStack();
                var cabinet=Placement.head(source);
                for(int x=-10;x<=21;x+=8)for(int z=0;z<16;z+=8)level.getChunk(ChunkPos.containing(cabinet.block(x,0,z)).x(),ChunkPos.containing(cabinet.block(x,0,z)).z());
                if(PartAssembly.generate(source,4)!=1)throw new AssertionError("Could not create test cabinet");
                for(var dimension:server.getAllLevels()){
                    BlockPos pos=dimension==level?player.blockPosition().offset(0,4,0):new BlockPos(0,80,0);
                    dimension.getChunk(ChunkPos.containing(pos).x(),ChunkPos.containing(pos).z());
                    // End dragon/ambient fluid updates must not invalidate the vanilla control.
                    dimension.setBlockAndUpdate(pos,Blocks.BEDROCK.defaultBlockState());markers.put(dimension,pos);
                    dimension.setBlockAndUpdate(pos.above(),BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraftchunithm:track")).defaultBlockState());
                    if(!dimension.getBlockState(pos).is(Blocks.BEDROCK)||!WorldUninstall.modBlock(dimension.getBlockState(pos.above())))
                        throw new AssertionError("Test marker placement failed in "+dimension.dimension().identifier()+" "+pos);
                }
                var text=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
                text.setText(Component.literal("vanilla control"));var pos=markers.get(level);text.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);level.addFreshEntity(text);control[0]=text.getUUID();
                level.setChunkForced(remoteChunk.x(),remoteChunk.z(),true);
                level.setBlockAndUpdate(remote,BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraftchunithm:track")).defaultBlockState());
                var orphan=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);orphan.setText(Component.literal("orphan"));
                orphan.addTag("minecraftchunithm.name_display");orphan.setPos(remote.getX()+.5,remote.getY()+1,remote.getZ()+.5);level.addFreshEntity(orphan);
                facadeOrphans(level,remote.offset(2,3,2));
                phase[0]=1;return;
            }
            if(phase[0]==1&&ticks[0]>40){server.saveEverything(true,true,true);level.setChunkForced(remoteChunk.x(),remoteChunk.z(),false);phase[0]=2;return;}
            if(phase[0]==2){
                if(level.getChunkSource().getChunkNow(remoteChunk.x(),remoteChunk.z())!=null||level.areEntitiesLoaded(remoteChunk.pack()))return;
                MinecraftChunithm.LOGGER.info("UNINSTALL TEST: remote unregistered block/entity confirmed unloaded before cleanup.");
                // The old getAllEntities-only query cannot see these residents.
                var hidden=new ChunkPos(400,-400);level.getChunk(hidden.x(),hidden.z());
                facadeOrphans(level,new BlockPos(hidden.getMinBlockX()+4,100,hidden.getMinBlockZ()+4));
                level.entityManager.updateChunkStatus(hidden,net.minecraft.world.level.entity.Visibility.HIDDEN);
                long stored=WorldUninstall.storedEntities(level,hidden).stream().filter(WorldUninstall::owned).count();
                long visible=java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false)
                        .filter(e->ChunkPos.containing(e.blockPosition()).equals(hidden)&&WorldUninstall.owned(e)).count();
                if(stored<3||visible!=0)throw new AssertionError("Could not reproduce hidden display storage outside visible lookup");
                MinecraftChunithm.LOGGER.info("UNINSTALL TEST: {} owned hidden displays are absent from getAllEntities() before cleanup.",stored);
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"chunithm uninstall");
                if(!WorldUninstall.running(server))throw new AssertionError("Uninstall command did not start");phase[0]=3;return;
            }
            if(phase[0]==3){
                if(WorldUninstall.running(server))return;
                var report=WorldUninstall.result(server);if(report==null||!report.complete()||report.failed()!=0||report.blocks()<7||report.entities()<10)
                    throw new AssertionError("Incomplete uninstall report: "+report);
                removed[0]=report.blocks();
                for(var entry:markers.entrySet()){
                    var dimension=entry.getKey();var pos=entry.getValue();
                    dimension.getChunk(ChunkPos.containing(pos).x(),ChunkPos.containing(pos).z());
                    if(!dimension.getBlockState(pos).is(Blocks.BEDROCK)||WorldUninstall.modBlock(dimension.getBlockState(pos.above())))
                        throw new AssertionError("Dimension cleanup mismatch in "+dimension.dimension().identifier()+" "+pos
                                +": vanilla="+dimension.getBlockState(pos)+", above="+dimension.getBlockState(pos.above()));
                    for(var entity:WorldUninstall.storedEntities(dimension,null))if(WorldUninstall.owned(entity))throw new AssertionError("Residual MOD display/item in entity sections");
                    for(var kind:PartConnections.Kind.values())if(!PartConnections.ofKind(dimension,kind).isEmpty())throw new AssertionError("Part cache not cleared");
                }
                if(level.getEntity(control[0])==null)throw new AssertionError("Uninstall removed unrelated vanilla TextDisplay");
                level.setChunkForced(remoteChunk.x(),remoteChunk.z(),true);phase[0]=4;return;
            }
            if(phase[0]==4){
                if(!level.areEntitiesLoaded(remoteChunk.pack()))return;
                if(!level.getBlockState(remote).isAir())throw new AssertionError("Unloaded unregistered MOD block survived");
                for(var entity:WorldUninstall.storedEntities(level,null))if(WorldUninstall.owned(entity))throw new AssertionError("Unloaded MOD entity survived");
                var saved=WorldUninstall.entityStorage(level).read(remoteChunk).join();
                if(saved.isPresent()&&WorldUninstall.containsEntities(saved.get()))throw new AssertionError("Saved remote display resurrected after cleanup");
                level.setChunkForced(remoteChunk.x(),remoteChunk.z(),false);
                PathCheck.requireNoRegistryFiles(server.getWorldPath(LevelResource.ROOT));
                // A second command should be a harmless, successful no-op.
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"chunithm uninstall");phase[0]=5;return;
            }
            if(phase[0]==5&&!WorldUninstall.running(server)){
                var report=WorldUninstall.result(server);
                if(report==null||!report.complete()||report.blocks()!=0||report.entities()!=0)throw new AssertionError("Uninstall is not idempotent: "+report);
                result="{\"passed\":true,\"blocksRemoved\":"+removed[0]+",\"dimensions\":"+markers.size()+",\"unloadedOrphans\":true,\"hiddenDisplays\":true,\"savedEntityVerification\":true,\"vanillaPreserved\":true,\"idempotent\":true}";
                MinecraftChunithm.LOGGER.info("UNINSTALL INTEGRATION PASSED: {}",result);
            }
        });
    }
    private static void facadeOrphans(ServerLevel level,BlockPos position){
        var text=EntityTypes.TEXT_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        text.setText(Component.literal("MAX COMBO: 1204 SCORE: 1,010,000"));text.addTag("minecraftchunithm.head_display");
        var socket=EntityTypes.BLOCK_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        socket.setBlockState(BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:cyan_concrete")).defaultBlockState());socket.addTag("minecraftchunithm.part_ports");
        var item=EntityTypes.ITEM_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
        stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,Identifier.parse("minecraftchunithm:cover/3000"));
        item.setItemStack(stack); // No ownership tag: model namespace alone is sufficient.
        int x=0;
        for(var entity:List.of(text,socket,item)){
            entity.setPos(position.getX()+x++*.25,position.getY(),position.getZ()+.5);level.addFreshEntity(entity);
        }
    }
    private static final class PathCheck {
        static void requireIsolated(java.nio.file.Path root){
            if(!root.toAbsolutePath().normalize().toString().replace('\\','/').contains("/build/air-input-test/"))
                throw new AssertionError("Refusing uninstall test outside isolated save: "+root);
        }
        static void requireNoRegistryFiles(java.nio.file.Path root){
            try(var files=Files.newDirectoryStream(root.resolve("data/minecraftchunithm"),"parts_*.json*")){
                if(files.iterator().hasNext())throw new AssertionError("Registry file survived");
            }catch(java.io.IOException error){throw new AssertionError(error);}
        }
    }
}
