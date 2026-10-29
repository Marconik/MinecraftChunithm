package io.github.marconik.minecraftchunithm;

import com.mojang.brigadier.CommandDispatcher;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.*;

final class CommandCheck {
    static void run(){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var messages=new ArrayList<String>();
        var receiver=new CommandSource(){
            public void sendSystemMessage(Component message){messages.add(message.getString());}
            public boolean acceptsSuccess(){return true;}
            public boolean acceptsFailure(){return true;}
            public boolean shouldInformAdmins(){return true;}
        };
        var source=new CommandSourceStack(receiver,Vec3.ZERO,Vec2.ZERO,null,PermissionSet.ALL_PERMISSIONS,Component.literal("check"),null);
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(TrackCommands.tree().then(ChartPlayback.commandTree()));
        for(String command:List.of("assemble","assemble 24","stop","uninstall","chart load 333","chart play","chart play 333 20 true",
                "chart pause","chart stop","chart seek 12.5","chart status","debug track_empty","debug track_empty 24",
                "debug test","debug test 24 120 20","debug head","debug ground_slide","debug air_sensors","debug nameplate","debug song",
                "debug head_data 0 0 0 0 0 0","debug song_data {}","debug nameplate_data {}")){
            var parsed=dispatcher.parse("chunithm "+command,source);
            if(parsed.getReader().canRead()||parsed.getContext().getCommand()==null)throw new AssertionError("Missing command: "+command);
        }
        for(String command:List.of("track 24","track_empty 24","test","head","ground_slide","air_sensors","nameplate","song",
                "head_data 0 0 0 0 0 0","song_data {}","nameplate_data {}","chart resume","debug track 24","assemble 1","assemble 1025")){
            var parsed=dispatcher.parse("chunithm "+command,source);
            if(!parsed.getReader().canRead()&&parsed.getContext().getCommand()!=null)throw new AssertionError("Obsolete/invalid command accepted: "+command);
        }
        CommandFeedback.generated(source.withSuppressedOutput(),"头部",null);
        CommandFeedback.generated(source.withSuppressedOutput(),"轨道",24);
        CommandFeedback.generated(source,"整机",24);
        if(!messages.equals(List.of("已生成整机，轨道长度为 24 格。")))throw new AssertionError("Assembly must emit a single concise confirmation");
        messages.clear();CommandFeedback.generated(source,"歌曲信息面板",null);
        if(!messages.equals(List.of("已生成歌曲信息面板。")))throw new AssertionError("Unexpected component confirmation");
        messages.clear();CommandFeedback.status(source,Component.literal("status probe"));
        CommandFeedback.failure(source,Component.literal("failure probe"));
        if(!messages.isEmpty())throw new AssertionError("Operational feedback leaked into chat/admin broadcasts");
        System.out.println("Commands: debug migration, removed aliases, argument bounds, concise generation, nested suppression and non-chat feedback passed.");
    }
}
