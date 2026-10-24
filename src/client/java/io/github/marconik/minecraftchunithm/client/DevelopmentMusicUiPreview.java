package io.github.marconik.minecraftchunithm.client;

import com.google.gson.*;
import io.github.marconik.minecraftchunithm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** Explicit render-only preview: runs at the title screen and never opens a world. */
final class DevelopmentMusicUiPreview {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.musicUiPreview"))return;
        int[] ticks={0};boolean[] started={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!started[0]){
                if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                started[0]=true;client.gui.setScreen(new Preview());
            }
            int tick=++ticks[0];
            if(tick==35||tick==75)Screenshot.grab(client.gameDirectory,tick==35?"music-queue-ui.png":"music-queue-empty.png",
                client.gameRenderer.mainRenderTarget(),1,message->MinecraftChunithm.LOGGER.info("Music UI preview: {}",message.getString()));
            if(tick==50&&client.gui.screen() instanceof Preview screen)screen.empty();
            if(tick==90)client.gui.setScreen(new SongPreview());
            if(tick==135&&client.gui.screen() instanceof SongPreview screen)screen.view.select(0);
            if(tick==170&&client.gui.screen() instanceof SongPreview screen)screen.scrollDown();
            if(tick==205&&client.gui.screen() instanceof SongPreview screen)screen.dragToBottom();
            if(tick==230&&client.gui.screen() instanceof SongPreview screen)screen.locked();
            if(tick==120||tick==165||tick==195||tick==220||tick==250)Screenshot.grab(client.gameDirectory,
                tick==120?"song-panel-queue.png":tick==165?"song-panel-catalog.png":tick==195?"song-panel-scrolled.png":tick==220?"song-panel-scroll-end.png":"song-panel-locked.png",
                client.gameRenderer.mainRenderTarget(),1,message->MinecraftChunithm.LOGGER.info("Song panel UI preview: {}",message.getString()));
            if(tick==270)client.stop();
        });
    }
    private static MusicQueue.Row song(String id,String state){
        try(var stream=DevelopmentMusicUiPreview.class.getResourceAsStream("/data/minecraftchunithm/songs/"+id+"/song.json")){
            if(stream==null)throw new IOException("Missing preview metadata");
            var song=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var chart=song.getAsJsonObject("charts").getAsJsonObject("MASTER");
            return new MusicQueue.Row(Integer.parseInt(id),song.get("title").getAsString(),song.get("artist").getAsString(),
                song.getAsJsonObject("cover").get("texture").getAsString(),song.getAsJsonObject("cover").get("itemModel").getAsString(),"MASTER",chart.get("level").getAsInt()+(chart.get("levelDecimal").getAsInt()>=50?"+":""),chart.get("internalLevel").getAsString(),state);
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    private static final class Preview extends Screen {
        private final HeadControlMenu menu=new HeadControlMenu(0,null);
        private MusicQueueView view;private float zoom;private int left,top;
        Preview(){super(Component.literal("Music queue development preview"));
            menu.receiveMusic(MusicQueue.encode(new MusicQueue.View(10,false,true,true,true,
                List.of(song("0180","PLAYED"),song("0103","READY"),song("1100","QUEUED")))));
        }
        @Override protected void init(){
            zoom=Math.min(1,Math.min((width-12f)/432,(height-12f)/256));left=(width-Math.round(432*zoom))/2;top=(height-Math.round(256*zoom))/2;
            view=new MusicQueueView(menu,font);view.init(left,top,zoom,b->addRenderableWidget(b),action->{},()->{});
        }
        void empty(){var empty=MusicQueue.View.empty();menu.receiveMusic(MusicQueue.encode(new MusicQueue.View(.25,true,true,true,false,empty.rows())));}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            g.pose().pushMatrix();g.pose().translate(left,top);g.pose().scale(zoom,zoom);view.draw(g);g.pose().popMatrix();
            super.extractRenderState(g,mx,my,delta);
        }
    }
    private static final class SongPreview extends Screen {
        private final SongPanelMenu menu=new SongPanelMenu(0,null);
        private final List<SongLibrary.Song> catalog=new ArrayList<>();
        private SongPanelView view;private float zoom;private int left,top,offset;
        SongPreview(){super(Component.literal("Song panel native development preview"));
            for(String id:List.of("0103","0180","0258","0465","0697","1100","2241","2354","2475")){
                try(var stream=DevelopmentMusicUiPreview.class.getResourceAsStream("/data/minecraftchunithm/songs/"+id+"/song.json")){
                    var metadata=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)).getAsJsonObject();
                    List<SongLibrary.Difficulty> difficulties=new ArrayList<>();
                    for(var entry:metadata.getAsJsonObject("charts").entrySet()){
                        var chart=entry.getValue().getAsJsonObject();
                        String constant=new java.math.BigDecimal(chart.get("internalLevel").getAsString()).stripTrailingZeros().toPlainString();if(!constant.contains("."))constant+=".0";
                        difficulties.add(new SongLibrary.Difficulty(entry.getKey(),chart.get("level").getAsInt()+(chart.get("levelDecimal").getAsInt()>=50?"+":""),constant,chart.get("imported").getAsBoolean()));
                    }
                    catalog.add(new SongLibrary.Song(Integer.parseInt(id),metadata.get("title").getAsString(),metadata.get("artist").getAsString(),metadata.getAsJsonObject("cover").get("texture").getAsString(),List.copyOf(difficulties)));
                }catch(IOException error){throw new IllegalStateException(error);}
            }
            update(new MusicQueue.View(10,false,true,true,true,MusicQueue.View.empty().rows()));
        }
        private void update(MusicQueue.View queue){
            menu.receive(SongPanelMenu.encode(new SongPanelMenu.State(queue,offset,catalog.size(),List.copyOf(catalog.subList(offset,Math.min(catalog.size(),offset+3))))));
        }
        private void request(int action){
            if(action>=SongPanelMenu.scrollAction(0)){offset=SongPanelMenu.clampOffset(action-SongPanelMenu.scrollAction(0),catalog.size());update(menu.state().queue());rebuildWidgets();}
            else if(action>=32){
                var selected=SongPanelMenu.selection(action);var rows=new ArrayList<>(menu.state().queue().rows());
                rows.set(selected.slot(),song(String.format(Locale.ROOT,"%04d",selected.songId()),"READY"));
                update(new MusicQueue.View(10,false,true,true,true,List.copyOf(rows)));
            }
        }
        void scrollDown(){
            if(!mouseScrolled(left+100*zoom,top+100*zoom,0,-1)||menu.state().offset()!=1)throw new IllegalStateException("Native song wheel scrolling failed");
        }
        void dragToBottom(){
            // Drag the production scrollbar through an actual widget rebuild caused by its response.
            var start=new net.minecraft.client.input.MouseButtonEvent(left+416*zoom,top+92*zoom,new net.minecraft.client.input.MouseButtonInfo(1,0));
            mouseClicked(start,false);
            var end=new net.minecraft.client.input.MouseButtonEvent(left+416*zoom,top+214*zoom,new net.minecraft.client.input.MouseButtonInfo(1,0));
            mouseDragged(end,0,122*zoom);mouseReleased(end);
            if(menu.state().offset()!=catalog.size()-3)throw new IllegalStateException("Native song scrollbar drag failed");
            MinecraftChunithm.LOGGER.info("Song selector native input checks passed: wheel advances one song, scrollbar reaches final three entries.");
        }
        void locked(){
            var rows=new ArrayList<>(MusicQueue.View.empty().rows());rows.set(0,song("0180","PLAYING"));
            update(new MusicQueue.View(10,false,true,true,false,List.copyOf(rows)));view.back();
        }
        @Override protected void init(){
            if(view==null)view=new SongPanelView(menu::state,font);
            zoom=Math.min(1.2f,Math.min((width-12f)/432,(height-12f)/view.height()));left=(width-Math.round(432*zoom))/2;top=(height-Math.round(view.height()*zoom))/2;
            view.init(left,top,zoom,b->addRenderableWidget(b),this::request,()->{},this::rebuildWidgets);
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            g.pose().pushMatrix();g.pose().translate(left,top);g.pose().scale(zoom,zoom);view.draw(g);g.pose().popMatrix();super.extractRenderState(g,mx,my,delta);
        }
        @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){return view.scroll(mx,my,vertical)||super.mouseScrolled(mx,my,horizontal,vertical);}
        @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){return view.dragScrollbar(event.y())||super.mouseDragged(event,dx,dy);}
        @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){boolean scrolling=view.releaseScrollbar();return super.mouseReleased(event)||scrolling;}
    }
}
