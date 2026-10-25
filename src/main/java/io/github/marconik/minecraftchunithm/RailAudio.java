package io.github.marconik.minecraftchunithm;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/** Server-authoritative transport, independent of any open control window. */
public final class RailAudio {
    private RailAudio(){}
    public record Snapshot(String dimension,long session,long revision,String event,String resource,
            int x,int y,int z,int direction,int length,double time,double offset,double duration,boolean playing) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"rail_audio"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,s)->{
            b.writeUtf(s.dimension,256);b.writeLong(s.session);b.writeLong(s.revision);
            b.writeUtf(s.event,256);b.writeUtf(s.resource,512);
            b.writeInt(s.x);b.writeInt(s.y);b.writeInt(s.z);b.writeVarInt(s.direction);b.writeVarInt(s.length);
            b.writeDouble(s.time);b.writeDouble(s.offset);b.writeDouble(s.duration);b.writeBoolean(s.playing);
        },b->new Snapshot(b.readUtf(256),b.readLong(),b.readLong(),b.readUtf(256),b.readUtf(512),
                b.readInt(),b.readInt(),b.readInt(),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void initialize(){PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);}
    static String dimension(ServerLevel level){return level.dimension().identifier().toString();}
    static void send(ServerLevel level,Snapshot snapshot){
        for(var player:level.players())if(ServerPlayNetworking.canSend(player,Snapshot.TYPE))ServerPlayNetworking.send(player,snapshot);
    }
    static void clear(ServerLevel level){send(level,new Snapshot(dimension(level),0,0,"","",0,0,0,0,0,0,0,0,false));}
}
