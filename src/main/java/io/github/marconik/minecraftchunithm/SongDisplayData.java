package io.github.marconik.minecraftchunithm;

import net.minecraft.resources.Identifier;

public record SongDisplayData(Identifier coverModel,String title,String artist,int track,Difficulty difficulty,
                              String level,double speed,boolean mirror) {
    public enum Difficulty { MASTER, EXPERT, ULTIMA }
    public SongDisplayData {
        if(coverModel==null||difficulty==null||title==null||artist==null||level==null||track<1
                ||!Double.isFinite(speed)||speed<MusicQueueData.MIN_SPEED||speed>MusicQueueData.MAX_SPEED||title.length()>256||artist.length()>256||level.length()>8)
            throw new IllegalArgumentException("Invalid song display data");
    }
    public static SongDisplayData defaults(){return new SongDisplayData(
            Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"cover/default"),"NO TRACK","UNKNOWN ARTIST",
            1,Difficulty.MASTER,"15+",MusicQueueData.DEFAULT_SPEED,false);}
}
