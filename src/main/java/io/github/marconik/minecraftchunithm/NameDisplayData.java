package io.github.marconik.minecraftchunithm;

import net.minecraft.resources.Identifier;

/** Presentation input only; no account, rating or character progression logic. */
public record NameDisplayData(String team,String title,TitleColor titleColor,String level,
                              String name,Identifier characterModel,String rating) {
    public enum TitleColor { SILVER, GOLD }
    public NameDisplayData {
        if(team==null||title==null||titleColor==null||level==null||name==null||characterModel==null||rating==null
                ||team.length()>256||title.length()>256||name.length()>256||level.length()>16||rating.length()>32)
            throw new IllegalArgumentException("Invalid name display data");
        if(team.contains("\n")||title.contains("\n")||level.contains("\n")||name.contains("\n")||rating.contains("\n")
                ||team.contains("\r")||title.contains("\r")||level.contains("\r")||name.contains("\r")||rating.contains("\r"))
            throw new IllegalArgumentException("Single-line fields required");
    }
    public static NameDisplayData defaults(){return new NameDisplayData("NO TEAM","WELCOME",TitleColor.SILVER,
            "1","PLAYER",Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"character/default"),"0.00");}
}
