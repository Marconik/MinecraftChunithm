package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.MinecraftChunithm;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Opt-in title-screen render/input check, with its own game/config directory. */
final class DevelopmentAudioUiPreview {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.audioUiPreview"))return;
        int[] ticks={0};boolean[] started={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!started[0]){
                if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                AudioVolumeSettings.set(false,1);AudioVolumeSettings.set(true,1);
                client.gui.setScreen(new Preview());started[0]=true;
            }
            int tick=++ticks[0];
            if(tick==35||tick==75)Screenshot.grab(client.gameDirectory,tick==35?"audio-volume-default.png":"audio-volume-limits.png",
                    client.gameRenderer.mainRenderTarget(),1,message->MinecraftChunithm.LOGGER.info("Audio UI preview: {}",message.getString()));
            if(tick==45&&client.gui.screen() instanceof Preview preview)preview.checkInput();
            if(tick==90){AudioVolumeSettings.set(false,1);AudioVolumeSettings.set(true,1);AudioVolumeSettings.save();client.stop();}
        });
    }
    private static final class Preview extends Screen {
        private float zoom;private int left,top;private AudioVolumeView view;
        Preview(){super(Component.literal("Audio volume preview"));}
        @Override protected void init(){
            zoom=Math.min(1,Math.min((width-12f)/432,(height-12f)/256));
            left=(width-Math.round(432*zoom))/2;top=(height-Math.round(256*zoom))/2;
            view=new AudioVolumeView(font);view.init(left,top,zoom,b->addRenderableWidget(b),()->{});
        }
        void checkInput(){
            RailMusicPlayback.verifyVolumeMix();NoteCuePlayback.verifyVolumeMix();
            var sliders=children().stream().filter(AbstractSliderButton.class::isInstance).map(AbstractSliderButton.class::cast).toList();
            for(int i=0;i<2;i++){
                var slider=sliders.get(i);
                for(double fraction:new double[]{0,.25,.5,.75,1}){
                    slider.onClick(new MouseButtonEvent(slider.getX()+4+fraction*(slider.getWidth()-8),slider.getY()+slider.getHeight()/2.0,
                            new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
                    double actual=i==0?AudioVolumeSettings.levels().system():AudioVolumeSettings.levels().effects();
                    if(Math.abs(actual-fraction*2)>1e-9)throw new AssertionError("Fader mouse position differs from multiplier");
                }
            }
            sliders.getFirst().onClick(new MouseButtonEvent(sliders.getFirst().getX(),sliders.getFirst().getY()+5,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
            AudioVolumeSettings.save();
            MinecraftChunithm.LOGGER.info("Audio UI native input: both faders passed 0, .5, 1, 1.5, 2 multipliers and saved preferences");
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            g.pose().pushMatrix();g.pose().translate(left,top);g.pose().scale(zoom,zoom);view.draw(g);g.pose().popMatrix();
            super.extractRenderState(g,mx,my,delta);
        }
    }
}
