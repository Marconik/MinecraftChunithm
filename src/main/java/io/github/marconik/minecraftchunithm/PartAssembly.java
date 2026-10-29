package io.github.marconik.minecraftchunithm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Convenience generation of the aligned arrangement shown by the reference layout. */
final class PartAssembly {
    static int generate(CommandSourceStack source,int length){
        if(!PartPorts.checkReady(source))return 0;
        var head=Placement.head(source);var level=source.getLevel();
        for(var kind:new PartConnections.Kind[]{PartConnections.Kind.HEAD,PartConnections.Kind.TRACK,PartConnections.Kind.PLAYER,PartConnections.Kind.SONG}){
            var p=PartConnections.target(head,kind);
            int width=kind==PartConnections.Kind.HEAD?11:kind==PartConnections.Kind.TRACK?9:7;
            int height=kind==PartConnections.Kind.HEAD?4:kind==PartConnections.Kind.TRACK?1:3;
            for(int z=0;z<(kind==PartConnections.Kind.TRACK?length:1);z++)for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                var pos=p.block(x,y,z);
                if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)){
                    CommandFeedback.failure(source,Component.literal("整机范围超出已加载区域或世界边界，未生成。"));return 0;
                }
            }
        }
        var ground=PartConnections.groundTarget(PartConnections.target(head,PartConnections.Kind.TRACK),length);
        for(int z=0;z<GroundSlide.DEPTH;z++)for(int x=0;x<GroundSlide.WIDTH;x++){
            var pos=ground.block(x,0,z);
            if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)){
                CommandFeedback.failure(source,Component.literal("整机 Ground Slide 范围超出已加载区域或世界边界，未生成。"));return 0;
            }
        }
        if(!AirSensors.preflight(source,ground))return 0;
        var silent=source.withSuppressedOutput();
        if(TrackHead.generate(silent)==0)return 0;
        var trackSource=silent.withPosition(Vec3.atBottomCenterOf(head.block(5,0,1).above()))
                .withRotation(new Vec2(0,head.forward().toYRot()));
        if(TrackCommands.generate(trackSource,length)==0)return 0;
        if(GroundSlide.generate(silent,ground)==0)return 0;
        if(AirSensors.generate(silent,ground)==0)return 0;
        var playerSource=silent.withPosition(Vec3.atBottomCenterOf(head.block(-1,1,0).relative(head.forward())))
                .withRotation(new Vec2(0,head.forward().getOpposite().toYRot()));
        var songSource=silent.withPosition(Vec3.atBottomCenterOf(head.block(11,1,0).relative(head.forward())))
                .withRotation(new Vec2(0,head.forward().getOpposite().toYRot()));
        if(NamePanel.generate(playerSource)==0||SongPanel.generate(songSource)==0)return 0;
        CommandFeedback.generated(source,"整机",length);
        return 1;
    }
}
