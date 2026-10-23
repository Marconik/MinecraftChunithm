package io.github.marconik.minecraftchunithm;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Registered structures, persisted per dimension. Connections require aligned, touching ports. */
public final class PartConnections {
    public enum Kind { HEAD, TRACK, PLAYER, SONG, GROUND_SLIDE, AIR_LEFT, AIR_RIGHT }
    public enum Status { CONNECTED, MISSING, UNLOADED, BROKEN }
    public record Link(String port,Status status){}
    public static final class Part {
        public final Kind kind; public final Placement placement; public final int length;
        public NameDisplayData player=NameDisplayData.defaults();
        public SongDisplayData song=SongDisplayData.defaults();
        Part(Kind kind,Placement placement,int length){this.kind=kind;this.placement=placement;this.length=length;}
        boolean air(){return kind==Kind.AIR_LEFT||kind==Kind.AIR_RIGHT;}
        int width(){return air()?AirSensors.BASE_WIDTH:kind==Kind.HEAD?11:kind==Kind.GROUND_SLIDE?GroundSlide.WIDTH:kind==Kind.TRACK?9:7;}
        int height(){return air()?AirSensors.BASE_HEIGHT+AirSensors.PANEL_HEIGHT:kind==Kind.HEAD?4:kind==Kind.TRACK||kind==Kind.GROUND_SLIDE?1:3;}
        int depth(){return air()?AirSensors.DEPTH:kind==Kind.TRACK?length:kind==Kind.GROUND_SLIDE?GroundSlide.DEPTH:1;}
        boolean occupied(int x,int y){return !air()||AirSensors.occupied(kind==Kind.AIR_LEFT,x,y);}
    }
    private static final Map<ServerLevel,List<Part>> CACHE=new WeakHashMap<>();
    private PartConnections(){}
    private static Path file(ServerLevel level){
        String dimension=level.dimension().identifier().toString().replace(':','_').replace('/','_');
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/minecraftchunithm/parts_"+dimension+".json");
    }
    private static List<Part> parts(ServerLevel level){return CACHE.computeIfAbsent(level,l->{
        List<Part> list=new ArrayList<>();Path path=file(l);if(!Files.exists(path))return list;
        try{
            for(var value:JsonParser.parseString(Files.readString(path)).getAsJsonArray()){
                var o=value.getAsJsonObject();var p=new Placement(new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt()),Direction.valueOf(o.get("facing").getAsString()));
                if(p.forward().getAxis().isVertical())throw new IllegalArgumentException("Vertical part");
                var part=new Part(Kind.valueOf(o.get("kind").getAsString()),p,o.get("length").getAsInt());
                if(part.length<1||part.length>1024)throw new IllegalArgumentException("Invalid length");
                if(o.has("player"))part.player=decodePlayer(o.getAsJsonObject("player"));
                if(o.has("song"))part.song=decodeSong(o.getAsJsonObject("song"));
                list.add(part);
            }
        }catch(Exception e){throw new IllegalStateException("Cannot read part registry "+path,e);}
        return list;
    });}
    public static void save(ServerLevel level){
        var array=new JsonArray();for(var part:parts(level)){
            var o=new JsonObject();o.addProperty("kind",part.kind.name());var pos=part.placement.origin();
            o.addProperty("x",pos.getX());o.addProperty("y",pos.getY());o.addProperty("z",pos.getZ());
            o.addProperty("facing",part.placement.forward().name());o.addProperty("length",part.length);
            if(part.kind==Kind.PLAYER)o.add("player",encodePlayer(part.player));
            if(part.kind==Kind.SONG)o.add("song",encodeSong(part.song));array.add(o);
        }
        try{Path path=file(level);Files.createDirectories(path.getParent());Path temporary=path.resolveSibling(path.getFileName()+".tmp");
            Files.writeString(temporary,array.toString());Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot save part registry",e);}
    }
    public static Part register(ServerLevel level,Kind kind,Placement p,int length){
        var list=parts(level);list.removeIf(part->{if(!overlaps(part,new Part(kind,p,length)))return false;PartPorts.remove(level,part);return true;});
        var part=new Part(kind,p,length);list.add(part);save(level);PartPorts.draw(level,part);return part;
    }
    private static boolean overlaps(Part a,Part b){
        var aa=new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atCenterOf(a.placement.block(0,0,0)),net.minecraft.world.phys.Vec3.atCenterOf(a.placement.block(a.width()-1,a.height()-1,a.depth()-1))).inflate(.01);
        var bb=new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atCenterOf(b.placement.block(0,0,0)),net.minecraft.world.phys.Vec3.atCenterOf(b.placement.block(b.width()-1,b.height()-1,b.depth()-1))).inflate(.01);
        // Compare occupied block centres, excluding adjacent structures.
        return aa.intersects(bb);
    }
    public static Part find(ServerLevel level,Kind kind,Placement p){return parts(level).stream().filter(a->a.kind==kind&&a.placement.equals(p)).findFirst().orElse(null);}
    static List<Part> ofKind(ServerLevel level,Kind kind){return parts(level).stream().filter(part->part.kind==kind).toList();}
    public static Part at(ServerLevel level,BlockPos pos){
        for(var part:parts(level))if(part.kind!=Kind.TRACK)for(int y=0;y<part.height();y++)for(int z=0;z<part.depth();z++)for(int x=0;x<part.width();x++)
            if(part.occupied(x,y)&&part.placement.block(x,y,z).equals(pos))return part;
        return null;
    }
    public static Status integrity(ServerLevel level,Part part){
        boolean broken=false;
        for(int y=0;y<part.height();y++)for(int z=0;z<part.depth();z++)for(int x=0;x<part.width();x++){
            if(!part.occupied(x,y))continue;
            var pos=part.placement.block(x,y,z);if(!level.hasChunkAt(pos))return Status.UNLOADED;
            var state=level.getBlockState(pos);var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String expected=switch(part.kind){case HEAD->"head_"+x+"_"+y;case PLAYER->"name_panel";case SONG->"song_panel";case TRACK->x==0?"track_left":x==8?"track_right":"track";case GROUND_SLIDE->"ground_slide_"+x+"_"+z;
                case AIR_LEFT,AIR_RIGHT->y==0?AirSensors.baseName(part.kind==Kind.AIR_LEFT,x,z):AirSensors.tileName(part.kind==Kind.AIR_LEFT,y-1,z);};
            if(!id.getNamespace().equals(MinecraftChunithm.MOD_ID)||!id.getPath().equals(expected))broken=true;
            else if(!state.hasProperty(HeadTile.FACING)||state.getValue(HeadTile.FACING)!=(part.kind==Kind.TRACK?part.placement.forward().getOpposite():part.placement.forward()))broken=true;
        }
        return broken?Status.BROKEN:Status.CONNECTED;
    }
    public static Placement target(Placement head,Kind kind){return switch(kind){
        case TRACK->new Placement(head.block(1,0,1),head.forward());
        case PLAYER->new Placement(head.block(-7,1,0),head.forward());
        case SONG->new Placement(head.block(11,1,0),head.forward());
        case GROUND_SLIDE->throw new IllegalArgumentException("Ground Slide target requires the track length");
        case AIR_LEFT,AIR_RIGHT->throw new IllegalArgumentException("AIR sensor target requires the Ground Slide placement");
        default->head;
    };}
    public static Placement groundTarget(Placement track,int length){return new Placement(track.block(-GroundSlide.TRACK_SIDE_EXTENSION,0,length),track.forward());}
    public static List<Link> links(ServerLevel level,Part head){
        List<Link> links=new ArrayList<>();var own=integrity(level,head);
        for(var kind:new Kind[]{Kind.TRACK,Kind.PLAYER,Kind.SONG}){
            var other=find(level,kind,target(head.placement,kind));
            var status=own!=Status.CONNECTED?own:other==null?Status.MISSING:integrity(level,other);
            if(kind==Kind.TRACK)for(String port:new String[]{"轨道左接口","轨道中接口","轨道右接口"})links.add(new Link(port,status));
            else links.add(new Link(kind==Kind.PLAYER?"左侧玩家面板":"右侧歌曲面板",status));
        }
        return links;
    }
    public static void playerUpdated(ServerLevel level,Placement p,NameDisplayData data){var a=find(level,Kind.PLAYER,p);if(a!=null){a.player=data;save(level);}}
    public static void songUpdated(ServerLevel level,Placement p,SongDisplayData data){var a=find(level,Kind.SONG,p);if(a!=null){a.song=data;save(level);}}
    private static JsonObject encodePlayer(NameDisplayData d){var o=new JsonObject();o.addProperty("team",d.team());o.addProperty("title",d.title());o.addProperty("titleColor",d.titleColor().name());o.addProperty("level",d.level());o.addProperty("name",d.name());o.addProperty("character",d.characterModel().toString());o.addProperty("rating",d.rating());return o;}
    private static NameDisplayData decodePlayer(JsonObject o){return new NameDisplayData(o.get("team").getAsString(),o.get("title").getAsString(),NameDisplayData.TitleColor.valueOf(o.get("titleColor").getAsString()),o.get("level").getAsString(),o.get("name").getAsString(),net.minecraft.resources.Identifier.parse(o.get("character").getAsString()),o.get("rating").getAsString());}
    private static JsonObject encodeSong(SongDisplayData d){var o=new JsonObject();o.addProperty("cover",d.coverModel().toString());o.addProperty("title",d.title());o.addProperty("artist",d.artist());o.addProperty("track",d.track());o.addProperty("difficulty",d.difficulty().name());o.addProperty("level",d.level());o.addProperty("speed",d.speed());o.addProperty("mirror",d.mirror());return o;}
    private static SongDisplayData decodeSong(JsonObject o){return new SongDisplayData(net.minecraft.resources.Identifier.parse(o.get("cover").getAsString()),o.get("title").getAsString(),o.get("artist").getAsString(),o.get("track").getAsInt(),SongDisplayData.Difficulty.valueOf(o.get("difficulty").getAsString()),o.get("level").getAsString(),o.get("speed").getAsDouble(),o.get("mirror").getAsBoolean());}
}
