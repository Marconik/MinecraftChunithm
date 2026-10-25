package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.MinecraftChunithm;
import io.github.marconik.minecraftchunithm.PlayerPanelMenu;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/** Opt-in native render check for account, guest and intentionally blank states. */
final class DevelopmentPlayerUiPreview {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.playerUiPreview"))return;
        int[] ticks={0};boolean[] started={false};PlayerPanelMenu[] menu={null};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            int tick=++ticks[0];
            if(!started[0]){
                if(tick<60||client.player==null||client.level==null||client.gui.overlay()!=null)return;
                started[0]=true;menu[0]=new PlayerPanelMenu(0,null);normal(menu[0]);
                client.gui.setScreen(new PlayerPanelScreen(menu[0],client.player.getInventory(),Component.literal("Player panel preview")));ticks[0]=0;return;
            }
            if(tick==35)screenshot(client,"player-account.png");
            if(tick==50)guest(menu[0]);
            if(tick==75)screenshot(client,"player-guest.png");
            if(tick==90)menu[0].receive(PlayerPanelMenu.encode(new PlayerPanelMenu.State(true,null)));
            if(tick==115)screenshot(client,"player-empty.png");
            if(tick==135)client.stop();
        });
    }
    private static void normal(PlayerPanelMenu menu){menu.receive(PlayerPanelMenu.encode(new PlayerPanelMenu.State(true,
            new PlayerPanelMenu.ProfileView("5276027620366606","Chunithm","1524","minecraftchunithm:textures/character/1524.png",
                    "1","17.58","NEW COMER","GOLD","GiGO",0))));}
    private static void guest(PlayerPanelMenu menu){menu.receive(PlayerPanelMenu.encode(new PlayerPanelMenu.State(true,
            new PlayerPanelMenu.ProfileView(PlayerPanelMenu.GUEST_ID,"GUEST","0000","minecraftchunithm:textures/character/0000.png",
                    "****","*****","","SILVER","",0))));}
    private static void screenshot(net.minecraft.client.Minecraft client,String name){
        Screenshot.grab(client.gameDirectory,name,client.gameRenderer.mainRenderTarget(),1,
                message->MinecraftChunithm.LOGGER.info("Player UI preview {}: {}",name,message.getString()));
    }
}
