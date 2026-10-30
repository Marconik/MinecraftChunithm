package io.github.marconik.minecraftchunithm.client;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Two local continuous mixer faders inside the head's existing modal frame. */
final class AudioVolumeView {
    private static final int GOLD=0xffffd74f,WHITE=0xfff5f5f5;
    // About 70% of the original 254 x 49 fader, leaving room for full nameplates.
    private static final int FADER_X=222,FADER_WIDTH=178,FADER_HEIGHT=34,FADER_Y=31;
    private final Font font;
    AudioVolumeView(Font font){this.font=font;}
    void init(int left,int top,float zoom,Consumer<AbstractWidget> add,Runnable close){
        var button=new Button(left+Math.round(406*zoom),top+Math.round(8*zoom),Math.round(18*zoom),Math.round(18*zoom),
                Component.literal("关闭窗口"),b->close.run(),message->message.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                g.pose().pushMatrix();g.pose().translate(getX(),getY());g.pose().scale(getWidth()/18f,getHeight()/18f);
                HeadControlScreen.bevel(g,0,0,18,18,isHoveredOrFocused()?0xffbababa:0xff939393,false);
                for(int i=0;i<8;i++){g.fill(5+i,5+i,7+i,7+i,WHITE);g.fill(11-i,5+i,13-i,7+i,WHITE);}
                g.pose().popMatrix();
            }
        };
        add.accept(button);
        for(int i=0;i<2;i++){
            int y=(i==0?42:144)+FADER_Y;
            add.accept(new Fader(left+Math.round(FADER_X*zoom),top+Math.round(y*zoom),Math.round(FADER_WIDTH*zoom),Math.round(FADER_HEIGHT*zoom),i==1));
        }
    }
    void draw(GuiGraphicsExtractor g){
        HeadControlScreen.bevel(g,0,0,432,256,0xffc6c6c6,false);
        g.fill(5,5,427,29,0xff565656);g.fill(5,5,7,29,GOLD);g.fill(5,29,427,31,0xff777777);
        text(g,"PLAYER / AUDIO SETTINGS",12,12,1.08f,WHITE,true);
        row(g,42,false);row(g,144,true);
    }
    private void row(GuiGraphicsExtractor g,int y,boolean effects){
        g.fill(12,y,420,y+92,0xff161616);g.fill(13,y+1,419,y+91,0xff303030);
        g.outline(13,y+1,406,90,0xff606060);g.fill(15,y+4,17,y+88,GOLD);
        if(effects)blit(g,"system_voice",60,y+10,104,71,367,250);
        else blit(g,"headphone",60,y+5,104,82,418,329);
        int start=FADER_X+4,end=FADER_X+FADER_WIDTH-4,center=Math.round((start+end)/2f);
        centered(g,"0x",start,y+75,.75f,0xffb7b7b7,false);
        centered(g,"1x",center,y+75,.75f,GOLD,false);
        centered(g,"2x",end,y+75,.75f,0xffb7b7b7,false);
        for(int i=0;i<=8;i++){
            int x=start+Math.round(i*(end-start)/8f);g.fill(x,y+65,x+1,y+(i%4==0?72:69),i==4?GOLD:0xff929292);
        }
        double value=effects?AudioVolumeSettings.levels().effects():AudioVolumeSettings.levels().system();
        centered(g,String.format(Locale.ROOT,"%.2fx",value),370,y+9,.95f,GOLD,true);
    }
    private static void blit(GuiGraphicsExtractor g,String name,int x,int y,int w,int h,int tw,int th){
        g.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("minecraftchunithm","textures/gui/audio/"+name+".png"),x,y,0,0,w,h,tw,th,tw,th);
    }
    private void text(GuiGraphicsExtractor g,String value,int x,int y,float scale,int color,boolean bold){
        var label=Component.literal(value).withStyle(s->s.withBold(bold));
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);g.text(font,label,0,0,color,false);g.pose().popMatrix();
    }
    private void centered(GuiGraphicsExtractor g,String value,int x,int y,float scale,int color,boolean bold){
        int w=font.width(Component.literal(value).withStyle(s->s.withBold(bold)));
        text(g,value,x-Math.round(w*scale/2),y,scale,color,bold);
    }
    private final class Fader extends AbstractSliderButton {
        private final boolean effects;
        Fader(int x,int y,int w,int h,boolean effects){
            super(x,y,w,h,Component.empty(),(effects?AudioVolumeSettings.levels().effects():AudioVolumeSettings.levels().system())/2);
            this.effects=effects;updateMessage();setTooltip(Tooltip.create(Component.literal(effects?"效果音量：过线提示音，叠加系统音量":"系统音量：乐曲及过线提示音")));
        }
        @Override protected void updateMessage(){setMessage(Component.literal((effects?"Effect volume ":"System volume ")+String.format(Locale.ROOT,"%.2fx",value*2)));}
        @Override protected void applyValue(){AudioVolumeSettings.set(effects,value*2);}
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            // Vanilla maps the mouse between x+4 and x+width-4. Map the ramp's
            // inner endpoints to those same pixels so pointer and hit region agree.
            float sx=(getWidth()-8f)/306,sy=getHeight()/100f;
            float origin=getX()+4-14*sx;
            int cursor=getX()+4+(int)Math.round(value*(getWidth()-8));
            g.pose().pushMatrix();g.pose().translate(origin,getY());g.pose().scale(sx,sy);
            blit(g,"rail",0,0,332,100,332,100);g.pose().popMatrix();
            g.enableScissor(getX(),getY(),cursor,getY()+getHeight());
            g.pose().pushMatrix();g.pose().translate(origin,getY());g.pose().scale(sx,sy);
            blit(g,"fill",0,0,332,100,332,100);g.pose().popMatrix();g.disableScissor();
            int top=getY()+Math.round((float)(70-55*value)*sy);
            g.fill(cursor-1,top-3,cursor+1,getY()+Math.round(87*sy),GOLD);
            float arrowScale=getHeight()/(float)FADER_HEIGHT;
            g.pose().pushMatrix();g.pose().translate(cursor,top-5*arrowScale);g.pose().scale(arrowScale,arrowScale);
            for(int i=0;i<5;i++)g.fill(-8+i*2,-10+i*2,10-i*2,-8+i*2,0xffffb800);
            g.pose().popMatrix();
            if(isHoveredOrFocused())g.outline(getX(),getY(),getWidth(),getHeight(),0xffb7b7b7);
            handleCursor(g);
        }
    }
}
