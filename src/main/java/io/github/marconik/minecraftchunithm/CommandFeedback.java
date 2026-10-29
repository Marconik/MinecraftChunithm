package io.github.marconik.minecraftchunithm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Generation confirmations go to chat; operational feedback uses the action bar. */
final class CommandFeedback {
    private CommandFeedback(){}
    static String generationText(String part,Integer length){
        return "已生成"+part+(length==null?"。":"，轨道长度为 "+length+" 格。");
    }
    static void generated(CommandSourceStack source,String part,Integer length){
        source.sendSuccess(()->Component.literal(generationText(part,length)),false);
    }
    static void status(CommandSourceStack source,Component message){
        if(source.getEntity() instanceof ServerPlayer player)notice(player,message);
        else MinecraftChunithm.LOGGER.info("{}",message.getString());
    }
    static void failure(CommandSourceStack source,Component message){
        if(source.getEntity() instanceof ServerPlayer player)notice(player,message);
        MinecraftChunithm.LOGGER.warn("{}",message.getString());
    }
    static void failure(ServerPlayer player,Component message){notice(player,message);MinecraftChunithm.LOGGER.warn("{}",message.getString());}
    static void notice(ServerPlayer player,Component message){player.sendOverlayMessage(message);}
}
