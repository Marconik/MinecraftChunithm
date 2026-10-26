package io.github.marconik.minecraftchunithm;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Explicit operator-only uninstall, using the live chunk/lighting/entity save APIs. */
public final class WorldUninstall {
    private static final Map<MinecraftServer,Job> JOBS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Result> RESULTS=new WeakHashMap<>();
    private static TicketType ticket;
    private static final Pattern REGION=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    public record Result(long blocks,long entities,int scanned,int failed,boolean complete){}
    private WorldUninstall(){}

    public static void initialize(){
        // A distinct, nonpersistent loading ticket: never alter the user's /forceload tickets.
        ticket=Registry.register(BuiltInRegistries.TICKET_TYPE,Identifier.parse("minecraftchunithm:uninstall"),
                new TicketType(0,TicketType.FLAG_LOADING|TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->
                world instanceof ServerLevel level&&running(level.getServer())
                        &&BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).getNamespace().equals(MinecraftChunithm.MOD_ID)
                        ?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var job=JOBS.get(server);if(job==null)return;
            try{job.tick();}catch(Exception error){job.fail("卸载中断",error);job.finish();}
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            var job=JOBS.remove(server);if(job!=null){job.release();job.message("卸载尚未完成，保留部件登记文件；请保留 MOD，下次重新执行 /chunithm uninstall。");}
        });
    }
    public static boolean running(MinecraftServer server){return JOBS.containsKey(server);}
    static Result result(MinecraftServer server){return RESULTS.get(server);}

    public static int start(CommandSourceStack source){
        var server=source.getServer();
        if(running(server)){source.sendFailure(Component.literal("卸载任务正在执行，请等待完成提示。"));return 0;}
        var job=new Job(server,source);JOBS.put(server,job);RESULTS.remove(server);
        try{job.prepare();return 1;}catch(Exception error){job.fail("无法准备卸载",error);job.finish();return 0;}
    }
    static boolean modBlock(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(MinecraftChunithm.MOD_ID);}
    static boolean owned(Entity entity){
        if(entity instanceof Display&&entity.entityTags().stream().anyMatch(t->t.startsWith(MinecraftChunithm.MOD_ID+".")))return true;
        if(entity instanceof Display.BlockDisplay block)return modBlock(block.getBlockState());
        var stack=entity instanceof Display.ItemDisplay item?item.getItemStack():entity instanceof ItemEntity item?item.getItem():null;
        if(stack==null||stack.isEmpty())return false;
        var model=stack.get(DataComponents.ITEM_MODEL);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(MinecraftChunithm.MOD_ID)
                ||model!=null&&model.getNamespace().equals(MinecraftChunithm.MOD_ID);
    }
    /** Only existing region-header entries; negative coordinates use floor-based region offsets. */
    static Map<ChunkPos,Boolean> savedChunks(Path dimension)throws IOException{
        var chunks=new LinkedHashMap<ChunkPos,Boolean>();
        for(String folder:List.of("region","entities")){
            Path directory=dimension.resolve(folder);if(!Files.isDirectory(directory))continue;
            try(var paths=Files.newDirectoryStream(directory,"r.*.*.mca")){
                for(Path path:paths){
                    var match=REGION.matcher(path.getFileName().toString());if(!match.matches())continue;
                    int rx=Integer.parseInt(match.group(1)),rz=Integer.parseInt(match.group(2));
                    try(var input=new DataInputStream(Files.newInputStream(path))){
                        for(int i=0;i<1024;i++)if(input.readInt()!=0){
                            var pos=new ChunkPos(Math.addExact(Math.multiplyExact(rx,32),i%32),Math.addExact(Math.multiplyExact(rz,32),i/32));
                            if(!pos.isValid())throw new IOException("Invalid region coordinate: "+path);
                            chunks.merge(pos,folder.equals("entities"),(a,b)->a||b);
                        }
                    }
                }
            }
        }
        return chunks;
    }
    static boolean containsBlocks(CompoundTag data){
        // Palettes can contain unused entries; a positive result only schedules a live scan.
        for(var section:data.getListOrEmpty("sections"))if(section instanceof CompoundTag value)
            for(var entry:value.getCompoundOrEmpty("block_states").getListOrEmpty("palette")){
                // 26.3 encodes default states as strings and other states as {id,
                // properties}; keep the earlier {Name, Properties} form readable too.
                String name=entry instanceof CompoundTag block?block.getStringOr("id",block.getStringOr("Name","")):entry.asString().orElse("");
                if(name.startsWith(MinecraftChunithm.MOD_ID+":"))return true;
            }
        return false;
    }
    private static final class Work {
        final ServerLevel level;final ChunkPos pos;final boolean entities;
        CompletableFuture<Optional<CompoundTag>> read;
        CompletableFuture<?> load;
        boolean ticketed;int waiting;
        Work(ServerLevel level,ChunkPos pos,boolean entities){this.level=level;this.pos=pos;this.entities=entities;}
        void release(){if(ticketed){level.getChunkSource().removeTicketWithRadius(ticket,pos,0);ticketed=false;}}
    }
    private static final class Job {
        final MinecraftServer server;final CommandSourceStack source;
        final Deque<Work> queued=new ArrayDeque<>();final List<Work> pending=new ArrayList<>();
        final Set<Path> dimensionPaths=new HashSet<>();
        long blocks,entities;int scanned,failed,total,ticks;
        Job(MinecraftServer server,CommandSourceStack source){this.server=server;this.source=source;}
        void message(String text){source.sendSuccess(()->Component.literal(text),true);}
        void fail(String text,Exception error){failed++;MinecraftChunithm.LOGGER.error(text,error);source.sendFailure(Component.literal(text+"："+error.getMessage()));}
        void prepare()throws IOException{
            message("开始卸载清理：将清除所有维度中的中二节奏方块和显示实体。请保持世界开启，等待完成提示。");
            for(var player:server.getPlayerList().getPlayers())if(player.containerMenu instanceof HeadControlMenu
                    ||player.containerMenu instanceof SongPanelMenu||player.containerMenu instanceof PlayerPanelMenu)player.closeContainer();
            for(var level:server.getAllLevels()){ChartPlayback.unload(level);TrackMotion.stop(level);}
            for(var level:server.getAllLevels())cleanMemory(level);
            // Flush pending writes first so newly placed/unsaved blocks are included in the scan.
            server.saveEverything(true,true,true);
            for(var level:server.getAllLevels()){
                Path path=DimensionType.getStorageFolder(level.dimension(),server.getWorldPath(LevelResource.ROOT)).toAbsolutePath().normalize();
                dimensionPaths.add(path);
                for(var entry:savedChunks(path).entrySet())queued.add(new Work(level,entry.getKey(),entry.getValue()));
                purge(level,null);
            }
            // Disabled/removed custom dimensions cannot be loaded safely by this server.
            Path custom=server.getWorldPath(LevelResource.ROOT).resolve("dimensions");
            if(Files.isDirectory(custom))try(var paths=Files.walk(custom)){
                for(Path path:paths.filter(Files::isDirectory).filter(p->p.getFileName().toString().equals("region")
                        ||p.getFileName().toString().equals("entities")).toList())
                    if(!dimensionPaths.contains(path.getParent().toAbsolutePath().normalize())&&!savedChunks(path.getParent()).isEmpty())
                        throw new IOException("存在未启用的维度，需先加载该维度再卸载："+path.getParent());
            }
            total=queued.size();message("已登记 "+total+" 个已保存区块，正在分批清理（包含未加载区域）。");
        }
        void tick(){
            ticks++;
            while(pending.size()<64&&!queued.isEmpty()){
                var work=queued.removeFirst();work.read=work.level.getChunkSource().chunkMap.read(work.pos);pending.add(work);
            }
            for(var it=pending.iterator();it.hasNext();){
                var work=it.next();
                try{if(!step(work))continue;}
                catch(Exception error){fail("无法清理 "+work.level.dimension().identifier()+" "+work.pos,error);}
                work.release();it.remove();scanned++;
            }
            if(queued.isEmpty()&&pending.isEmpty()){finish();return;}
            if(ticks%100==0)message("卸载进度："+scanned+"/"+total+" 区块，已清除 "+blocks+" 方块、"+entities+" 实体。");
        }
        boolean step(Work work)throws IOException{
            if(work.load==null){
                if(!work.read.isDone())return false;
                var data=work.read.join();
                if(data.isEmpty()){
                    if(work.entities)throw new IOException("实体区块缺少对应地形数据");
                    return true;
                }
                boolean mod=containsBlocks(data.get());
                if(!data.get().getStringOr("Status","").equals("minecraft:full")){
                    if(mod||work.entities)throw new IOException("含待检查内容的区块尚未生成完整，未对其强制生成");
                    return true;
                }
                if(!mod&&!work.entities)return true;
                if(pending.stream().filter(w->w.ticketed).count()>=8)return false;
                work.ticketed=true;work.load=work.level.getChunkSource().addTicketAndLoadWithRadius(ticket,work.pos,0);
                return false;
            }
            if(++work.waiting>1200)throw new IOException("等待区块或实体加载超时");
            if(!work.load.isDone())return false;work.load.join();
            var chunk=work.level.getChunkSource().getChunkNow(work.pos.x(),work.pos.z());
            if(chunk==null||!work.level.areEntitiesLoaded(work.pos.pack()))return false;
            blocks+=clearBlocks(work.level,chunk);purge(work.level,work.pos);return true;
        }
        void purge(ServerLevel level,ChunkPos pos){
            var remove=new ArrayList<Entity>();
            for(var entity:level.getAllEntities())if((pos==null||ChunkPos.containing(entity.blockPosition()).equals(pos))&&owned(entity))remove.add(entity);
            for(var entity:remove){entity.discard();entities++;}
        }
        void cleanMemory(ServerLevel level){
            // Saving skips some non-ticking/pending-unload holders. Include their live
            // state explicitly, then write via Minecraft's serializer and I/O worker.
            var map=level.getChunkSource().chunkMap;
            var holders=new HashSet<>(map.visibleChunkMap.values());holders.addAll(map.pendingUnloads.values());
            for(var holder:holders){
                var latest=holder.getLatestChunk();
                var chunk=latest instanceof LevelChunk full?full:latest instanceof ImposterProtoChunk proxy?proxy.getWrapped():null;
                if(chunk==null)continue;
                long count=clearBlocks(level,chunk);if(count==0)continue;blocks+=count;
                map.write(chunk.getPos(),SerializableChunkData.copyOf(level,chunk).write()).join();
            }
            map.synchronize(true).join();
        }
        void release(){for(var work:pending)work.release();}
        void finish(){
            release();
            try{
                if(failed==0){
                    for(var level:server.getAllLevels()){cleanMemory(level);purge(level,null);}
                    PartConnections.clearForUninstall(server);
                    server.saveEverything(true,true,true);
                }
            }catch(Exception error){fail("卸载保存失败",error);}
            RESULTS.put(server,new Result(blocks,entities,scanned,failed,failed==0));JOBS.remove(server);
            if(failed==0)message("卸载清理完成并已保存：清除 "+blocks+" 个 MOD 方块、"+entities+" 个相关实体。现在可保存退出、关闭游戏并删除 MOD 文件。");
            else source.sendFailure(Component.literal("卸载未完成（"+failed+" 处错误），已清除 "+blocks+" 方块、"+entities+" 实体。请保留 MOD，处理错误后重试。"));
        }
    }
    static long clearBlocks(ServerLevel level,LevelChunk chunk){
        long removed=0;var pos=new BlockPos.MutableBlockPos();var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++){
            var section=sections[i];if(!section.maybeHas(WorldUninstall::modBlock))continue;
            int bottom=chunk.getMinY()+i*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(modBlock(section.getBlockState(x,y,z))){
                pos.set(chunk.getPos().getMinBlockX()+x,bottom+y,chunk.getPos().getMinBlockZ()+z);
                if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))
                    throw new IllegalStateException("无法清除 MOD 方块："+pos);
                removed++;
            }
        }
        return removed;
    }
}
