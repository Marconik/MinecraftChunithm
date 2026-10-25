package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Send the note schedule once per listener; only its clock and sensor state are periodic. */
public final class NoteCueAudio {
    private NoteCueAudio(){}
    public static final int MAX_TIMES=200000,CHUNK_SIZE=4096;
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,name);}
    public record Timeline(long session,int offset,int total,List<Double> times) implements CustomPacketPayload {
        public Timeline {times=List.copyOf(times);}
        public static final Type<Timeline> TYPE=new Type<>(id("note_cue_timeline"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Timeline> CODEC=StreamCodec.of((b,s)->{
            b.writeLong(s.session);b.writeVarInt(s.offset);b.writeVarInt(s.total);b.writeVarInt(s.times.size());
            for(double time:s.times)b.writeDouble(time);
        },b->{long session=b.readLong();int offset=b.readVarInt(),total=b.readVarInt(),size=b.readVarInt();
            if(total<0||total>MAX_TIMES||offset<0||size<0||size>CHUNK_SIZE||offset>total-size)throw new IllegalArgumentException("Invalid note timeline size");
            var times=new ArrayList<Double>(size);for(int i=0;i<size;i++)times.add(b.readDouble());
            return new Timeline(session,offset,total,times);
        });
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record State(String dimension,long session,long revision,double time,boolean playing,int mask,Vec3 left,Vec3 right) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(id("note_cue_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,s)->{
            b.writeUtf(s.dimension,256);b.writeLong(s.session);b.writeLong(s.revision);b.writeDouble(s.time);b.writeBoolean(s.playing);b.writeVarInt(s.mask);
            for(var p:List.of(s.left,s.right)){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
        },b->new State(b.readUtf(256),b.readLong(),b.readLong(),b.readDouble(),b.readBoolean(),b.readVarInt(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void initialize(){
        PayloadTypeRegistry.clientboundPlay().register(Timeline.TYPE,Timeline.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE,State.CODEC);
    }
    /** Centre of the raised sensor's inner emitting face, at the middle aperture. */
    public static Vec3 position(Placement sensor,boolean left){
        return sensor.point(left?1:AirSensors.BASE_WIDTH-1,AirSensors.portHeight(AirSensors.PORT_COUNT/2),AirSensors.DEPTH/2.0);
    }
    public static void clear(ServerLevel level){
        var state=new State(RailAudio.dimension(level),0,0,0,false,0,Vec3.ZERO,Vec3.ZERO);
        for(var player:level.players())if(ServerPlayNetworking.canSend(player,State.TYPE))ServerPlayNetworking.send(player,state);
    }
    static final class Transport {
        private final List<Double> times;private final Set<net.minecraft.server.level.ServerPlayer> sent=new HashSet<>();
        Transport(ChartDefinition chart){times=NoteCueTimeline.of(chart);}
        void sync(ServerLevel level,PartConnections.Part track,long session,long revision,double time,boolean playing){
            var p=track.placement;
            var route=PartConnections.inputRoute(level,new Placement(p.block(-1,0,-1),p.forward()));
            boolean left=route.leftAir().connected(),right=route.rightAir().connected();
            var state=new State(RailAudio.dimension(level),session,revision,time,playing,(left?1:0)|(right?2:0),
                    left?position(route.leftAir().part().placement,true):Vec3.ZERO,
                    right?position(route.rightAir().part().placement,false):Vec3.ZERO);
            sent.retainAll(level.players());
            for(var player:level.players()){
                if(!ServerPlayNetworking.canSend(player,Timeline.TYPE)||!ServerPlayNetworking.canSend(player,State.TYPE))continue;
                if(sent.add(player)){
                    if(times.isEmpty())ServerPlayNetworking.send(player,new Timeline(session,0,0,List.of()));
                    for(int start=0;start<times.size();start+=CHUNK_SIZE)
                        ServerPlayNetworking.send(player,new Timeline(session,start,times.size(),times.subList(start,Math.min(start+CHUNK_SIZE,times.size()))));
                }
                ServerPlayNetworking.send(player,state);
            }
        }
    }
}
