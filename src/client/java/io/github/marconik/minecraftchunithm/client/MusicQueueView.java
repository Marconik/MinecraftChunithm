package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;

/** Modal view of the same server menu, with native focusable, narrated widgets. */
final class MusicQueueView {
    private static final int PURPLE=0xffd96cff,WHITE=0xfff5f5f5,MUTED=0xffb7b7b7;
    private final Supplier<MusicQueue.View> state;
    private final boolean selectionMode;
    private final Font font;
    private final List<Key> keys=new ArrayList<>();
    private IntConsumer request;
    MusicQueueView(HeadControlMenu menu,Font font){this(menu::musicView,font,false);}
    MusicQueueView(Supplier<MusicQueue.View> state,Font font,boolean selectionMode){this.state=state;this.font=font;this.selectionMode=selectionMode;}
    int height(){return selectionMode?216:256;}
    private int rowTop(int slot){return (selectionMode?43:83)+slot*54;}
    void init(int left,int top,float zoom,Consumer<Button> add,IntConsumer request,Runnable close){
        init(left,top,zoom,add,request,close,slot->{});
    }
    void init(int left,int top,float zoom,Consumer<Button> add,IntConsumer request,Runnable close,IntConsumer select){
        this.request=request;
        key(left,top,zoom,add,406,8,18,18,-1,"关闭窗口",close,1);
        if(!selectionMode){
            key(left,top,zoom,add,102,41,22,26,HeadControlMenu.SPEED_UP,"速度 +0.25",null,2);
            key(left,top,zoom,add,202,41,22,26,HeadControlMenu.SPEED_DOWN,"速度 -0.25",null,3);
            key(left,top,zoom,add,346,43,60,22,HeadControlMenu.MIRROR,"切换镜像",null,4);
        }else for(int slot=0;slot<3;slot++){
            int index=slot;key(left,top,zoom,add,362,rowTop(slot)+16,49,20,-1,"选择 TRACK "+(slot+1),()->select.accept(index),5);
        }
        for(int slot=0;slot<3;slot++)add.accept(new Info(left+Math.round(98*zoom),top+Math.round((rowTop(slot)+4)*zoom),Math.round(254*zoom),Math.round(43*zoom),slot));
    }
    private void key(int left,int top,float zoom,Consumer<Button> add,int x,int y,int w,int h,int action,String tip,Runnable callback,int icon){
        var key=new Key(left+Math.round(x*zoom),top+Math.round(y*zoom),Math.round(w*zoom),Math.round(h*zoom),w,h,action,tip,callback,icon);
        keys.add(key);add.accept(key);
    }
    void draw(GuiGraphicsExtractor g){
        var view=state.get();
        frame(g,selectionMode?"MUSIC / TRACK SELECT":"MUSIC / PLAY QUEUE",height());
        if(!selectionMode){
        panel(g,12,36,232,39);panel(g,254,36,166,39);
        text(g,"SPEED",24,49,70,1,true,WHITE);
        HeadControlScreen.bevel(g,132,43,62,22,0xff202020,true);
        center(g,String.format(Locale.ROOT,"%.2f",view.speed()),163,50,true,WHITE);
        text(g,"MIRROR",266,49,75,1,true,WHITE);
        }
        for(int slot=0;slot<3;slot++)row(g,slot,view.rows().get(slot));
        if(!view.connected())text(g,"OFFLINE",308,13,62,.8f,true,0xffffa4a4);
        else if(selectionMode&&view.writable()&&!view.canSelect())text(g,"LOCKED",348,13,52,.8f,true,0xffffd74f);
        for(var key:keys)key.update();
    }
    void frame(GuiGraphicsExtractor g,String title,int height){
        HeadControlScreen.bevel(g,0,0,432,height,0xffc6c6c6,false);
        g.fill(5,5,427,29,0xff565656);g.fill(5,29,427,31,0xff777777);
        g.fill(5,5,7,29,PURPLE);
        text(g,title,12,12,350,1.08f,true,WHITE);
    }
    private void row(GuiGraphicsExtractor g,int index,MusicQueue.Row row){
        int y=rowTop(index);
        boolean filled=!row.status().equals("EMPTY"),played=row.status().equals("PLAYED");
        int accent=played||!filled?0xff818181:PURPLE;
        panel(g,12,y,408,51);
        g.fill(15,y+4,17,y+47,accent);
        g.fill(21,y+11,49,y+39,WHITE);g.fill(22,y+12,48,y+38,0xffe60088);
        inkCenter(g,"TRACK",35,y+16,true,WHITE,.58f);
        // Bitmap digits retain a transparent right bearing inside their glyph quad.
        // Compensate at this scale so the visible number shares TRACK's centre.
        inkCenter(g,Integer.toString(index+1),37,y+29,true,WHITE,2f);
        g.fill(55,y+6,94,y+45,0xff171717);g.outline(55,y+6,39,39,0xff777777);
        if(!filled){
            g.fill(57,y+8,92,y+43,0xffbfbfbf);g.outline(57,y+8,35,35,0xffff2638);
            for(int i=1;i<34;i++)g.fill(57+i,y+8+i,58+i,y+10+i,0xffff2638);
            text(g,"No Data",101,y+10,144,1.25f,true,WHITE);
            text(g,"No Data",101,y+29,144,.8f,false,MUTED);
            g.fill(253,y+14,354,y+37,0xff555555);g.outline(253,y+14,101,23,0xff818181);
            return;
        }
        if(!row.texture().isEmpty()){
            g.blit(RenderPipelines.GUI_TEXTURED,Identifier.parse(row.texture()),57,y+8,0,0,35,35,300,300,300,300);
        }else if(!row.coverModel().isEmpty()){
            var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.ITEM_MODEL,Identifier.parse(row.coverModel()));
            g.pose().pushMatrix();g.pose().translate(57,y+8);g.pose().scale(35f/16,35f/16);g.fakeItem(stack,0,0);g.pose().popMatrix();
        }else{
            g.fill(57,y+8,92,y+43,0xff424242);g.fill(64,y+24,85,y+27,0xff737373);
        }
        text(g,row.title(),101,y+10,144,1.25f,true,played?0xffcccccc:WHITE);
        text(g,row.artist(),101,y+29,144,.8f,false,MUTED);
        if(filled){
            int difficultyColor=switch(row.difficulty()){case "EXPERT"->0xffc7414d;case "ULTIMA"->0xff111111;case "MASTER"->0xff8a38b5;default->0xff477c67;};
            g.fill(253,y+14,354,y+37,difficultyColor);g.outline(253,y+14,101,23,0xffbdbdbd);
            text(g,row.difficulty(),267,y+22,47,.88f,true,WHITE);
            int levelX=267+Math.round(font.width(Component.literal("MASTER ").withStyle(s->s.withBold(true)))*.88f);
            text(g,row.level(),levelX,y+22,350-levelX,.88f,true,WHITE);
        }
        int statusColor=played||!filled||row.status().equals("MISSING")?0xffaaaaaa:0xff85d9ff;
        if(!selectionMode)center(g,played?"PLAYED":"READY",389,y+22,true,statusColor,.8f);
    }
    void panel(GuiGraphicsExtractor g,int x,int y,int w,int h){
        g.fill(x,y,x+w,y+h,0xff161616);g.fill(x+1,y+1,x+w-1,y+h-1,0xff303030);
        g.outline(x+1,y+1,w-2,h-2,0xff606060);g.fill(x+3,y+3,x+w-3,y+4,0xff464646);
    }
    void text(GuiGraphicsExtractor g,String value,int x,int y,int max,float scale,boolean bold,int color){
        String shown=value;Component label=Component.literal(shown).withStyle(s->s.withBold(bold));
        if(font.width(label)*scale>max){
            while(!shown.isEmpty()&&font.width(Component.literal(shown+"…").withStyle(s->s.withBold(bold)))*scale>max)
                shown=shown.substring(0,shown.offsetByCodePoints(shown.length(),-1));
            label=Component.literal(shown+"…").withStyle(s->s.withBold(bold));
        }
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);g.text(font,label,0,0,color,false);g.pose().popMatrix();
    }
    private void center(GuiGraphicsExtractor g,String value,int x,int y,boolean bold,int color){center(g,value,x,y,bold,color,1);}
    /** Center the visible glyph quad, excluding font advance and baseline padding. */
    void inkCenter(GuiGraphicsExtractor g,String value,float x,float y,boolean bold,int color,float scale){
        var label=Component.literal(value).withStyle(s->s.withBold(bold));
        var bounds=font.prepareText(label.getVisualOrderText(),0,0,color,false,false,0).bounds();
        if(bounds==null)return;
        g.pose().pushMatrix();g.pose().translate(x-(bounds.left()+bounds.width()/2f)*scale,y-(bounds.top()+bounds.height()/2f)*scale);
        g.pose().scale(scale,scale);g.text(font,label,0,0,color,false);g.pose().popMatrix();
    }
    private void center(GuiGraphicsExtractor g,String value,int x,int y,boolean bold,int color,float scale){
        var label=Component.literal(value).withStyle(s->s.withBold(bold));
        text(g,value,x-Math.round(font.width(label)*scale/2),y,100,scale,bold,color);
    }
    private final class Key extends Button {
        final int logicalW,logicalH,action,icon;final Runnable callback;
        private long pressedUntil;
        Key(int x,int y,int w,int h,int lw,int lh,int action,String tip,Runnable callback,int icon){
            super(x,y,w,h,Component.literal(tip),button->{},DEFAULT_NARRATION);
            logicalW=lw;logicalH=lh;this.action=action;this.callback=callback;this.icon=icon;
            setTooltip(Tooltip.create(Component.literal(tip)));setTooltipDelay(java.time.Duration.ofMillis(350));update();
        }
        void update(){
            var view=state.get();active=action<0||view.writable();
            if(icon==5)active=view.canSelect();
            if(action==HeadControlMenu.SPEED_UP)active&=view.speed()<MusicQueueData.MAX_SPEED;
            if(action==HeadControlMenu.SPEED_DOWN)active&=view.speed()>MusicQueueData.MIN_SPEED;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers input){
            pressedUntil=System.nanoTime()+100_000_000L;if(callback!=null)callback.run();else request.accept(action);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            boolean hover=active&&isHoveredOrFocused(),pressed=active&&System.nanoTime()<pressedUntil;
            g.pose().pushMatrix();g.pose().translate(getX(),getY());g.pose().scale((float)getWidth()/logicalW,(float)getHeight()/logicalH);
            int face=active?(pressed?0xff6e6e6e:hover?0xffa7a7a7:0xff858585):0xff777777;
            if(active&&(icon==2||icon==3))face=pressed?0xff858585:hover?0xffb8b8b8:0xffa7a7a7;
            if(icon==4)face=state.get().mirror()?(pressed?0xff858585:0xffa7a7a7):(pressed?0xff292929:0xff3b3b3b);
            HeadControlScreen.bevel(g,0,0,logicalW,logicalH,face,pressed);
            int color=active?WHITE:0xff525252;
            if(icon==1)for(int i=0;i<8;i++){g.fill(5+i,5+i,7+i,7+i,color);g.fill(11-i,5+i,13-i,7+i,color);}
            if(icon==2||icon==3)for(int i=0;i<6;i++){
                int y=icon==2?8+i:17-i;
                g.fill(logicalW/2-i,y,logicalW/2+i+1,y+1,active?(icon==2?0xff68ef91:0xffff7878):color);
            }
            if(icon==4){
                boolean mirror=state.get().mirror();center(g,mirror?"ON":"OFF",logicalW/2,7,true,active?(mirror?WHITE:MUTED):0xff858585);
            }
            if(icon==5)inkCenter(g,"SELECT",logicalW/2f,logicalH/2f,true,active?WHITE:0xffbbbbbb,.9f);
            if(hover){g.outline(0,0,logicalW,logicalH,PURPLE);g.outline(-1,-1,logicalW+2,logicalH+2,0x40d96cff);}
            g.pose().popMatrix();
        }
    }
    private final class Info extends Button {
        private final int slot;private String previous="";
        Info(int x,int y,int w,int h,int slot){
            super(x,y,w,h,Component.literal("曲目信息"),button->{},DEFAULT_NARRATION);this.slot=slot;
            setTooltipDelay(java.time.Duration.ofMillis(350));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            var row=state.get().rows().get(slot);
            String description=row.status().equals("EMPTY")?"":row.title()+"\n"+row.artist();
            if(!description.equals(previous)){previous=description;setMessage(Component.literal(description));setTooltip(description.isEmpty()?null:Tooltip.create(Component.literal(description)));}
            if(isFocused())g.outline(getX(),getY(),getWidth(),getHeight(),PURPLE);
        }
    }
}
