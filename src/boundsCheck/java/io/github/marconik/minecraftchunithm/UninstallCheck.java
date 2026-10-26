package io.github.marconik.minecraftchunithm;

import java.io.DataOutputStream;
import java.nio.file.Files;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

final class UninstallCheck {
    static void run(){
        // ChunkPos.isValid consults the vanilla chunk-generation registry.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var palette=new ListTag();var block=new CompoundTag();block.putString("Name","minecraft:stone");palette.add(block);
        var states=new CompoundTag();states.put("palette",palette);
        var section=new CompoundTag();section.put("block_states",states);
        var sections=new ListTag();sections.add(section);
        var chunk=new CompoundTag();chunk.put("sections",sections);
        if(WorldUninstall.containsBlocks(chunk))throw new AssertionError("Vanilla palette marked for block removal");
        block.putString("Name","minecraftchunithm_other:track");
        if(WorldUninstall.containsBlocks(chunk))throw new AssertionError("Other namespace marked for block removal");
        block.putString("Name","minecraftchunithm:track");
        if(!WorldUninstall.containsBlocks(chunk))throw new AssertionError("Orphan MOD palette ignored");
        block.remove("Name");block.putString("id","minecraftchunithm:track");
        if(!WorldUninstall.containsBlocks(chunk))throw new AssertionError("26.3 state object ignored");
        palette.clear();palette.add(net.minecraft.nbt.StringTag.valueOf("minecraftchunithm:track"));
        if(!WorldUninstall.containsBlocks(chunk))throw new AssertionError("26.3 default-state string ignored");
        palette.clear();palette.add(net.minecraft.nbt.StringTag.valueOf("minecraft:diamond_block"));
        if(WorldUninstall.containsBlocks(chunk))throw new AssertionError("Vanilla state string incorrectly accepted");
        try{
            var root=Files.createTempDirectory("chunithm-uninstall-check-");
            var region=Files.createDirectory(root.resolve("region"));var entities=Files.createDirectory(root.resolve("entities"));
            var blocks=region.resolve("r.-2.3.mca");var entity=entities.resolve("r.-2.3.mca");
            try{
                try(var out=new DataOutputStream(Files.newOutputStream(blocks))){for(int i=0;i<1024;i++)out.writeInt(i==0||i==1023?513:0);}
                try(var out=new DataOutputStream(Files.newOutputStream(entity))){for(int i=0;i<1024;i++)out.writeInt(i==1023?513:0);}
                var found=WorldUninstall.savedChunks(root);
                if(found.size()!=2||!Boolean.FALSE.equals(found.get(new ChunkPos(-64,96)))||!Boolean.TRUE.equals(found.get(new ChunkPos(-33,127))))
                    throw new AssertionError("Region/entity union or negative region coordinates incorrect");
                Files.write(blocks,new byte[5]);
                try{WorldUninstall.savedChunks(root);throw new AssertionError("Truncated region incorrectly accepted");}
                catch(java.io.EOFException expected){}
            }finally{Files.deleteIfExists(blocks);Files.deleteIfExists(entity);Files.delete(entities);Files.delete(region);Files.delete(root);}
        }catch(java.io.IOException error){throw new AssertionError(error);}
        System.out.println("Uninstall scan: exact namespace filtering, orphan palettes, negative region coordinates, block/entity chunk union and truncated-file rejection passed.");
    }
}
