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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import static com.mojang.blaze3d.platform.InputConstants.*;

/** Shared native view for the song panel's queue and vertically scrolling selector. */
final class SongPanelView {
    private static final int ULTIMA_COLOR=0xff21112e;
    private static final int LIST_TOP=70,LIST_HEIGHT=176,TAB_LEFT=36,TAB_RIGHT=396;
    private final Supplier<SongPanelMenu.State> state;
    private final Font font;
    private final List<ActionButton> buttons=new ArrayList<>();
    private MusicQueueView queue;
    private int selectedSlot=-1;
    private IntConsumer request;
    private Runnable rebuild;
    private int left,top,targetOffset=-1;
    private float zoom;
    private double wheelRemainder,grabOffset;
    private boolean dragging;
    private Scrollbar scrollbar;
    private int lastGenreId=Integer.MIN_VALUE;
    private double tabScroll,tabTarget,tabFrom,tabMax;
    private long tabAnimationStart;
    SongPanelView(Supplier<SongPanelMenu.State> state,Font font){this.state=state;this.font=font;}
    int height(){return selectedSlot<0?216:286;}
    boolean selecting(){return selectedSlot>=0;}
    void select(int slot){if(state.get().queue().canSelect()){selectedSlot=slot;dragging=false;rebuild.run();}}
    void back(){selectedSlot=-1;dragging=false;rebuild.run();}
    Object catalog(){return List.of(state.get().offset(),state.get().total(),state.get().songs(),state.get().genreId(),state.get().genres());}
    private int offset(){return targetOffset<0?state.get().offset():SongPanelMenu.clampOffset(targetOffset,state.get().total());}
    private void scrollTo(int value){
        int next=SongPanelMenu.clampOffset(value,state.get().total());
        if(next==offset())return;
        targetOffset=next;request.accept(SongPanelMenu.scrollAction(next));
    }
    boolean scroll(double mx,double my,double amount){
        if(!selecting()||mx<left+12*zoom||mx>=left+420*zoom||my<top+LIST_TOP*zoom||my>=top+(LIST_TOP+LIST_HEIGHT)*zoom)return false;
        wheelRemainder+=amount;int steps=(int)wheelRemainder;wheelRemainder-=steps;
        if(steps!=0)scrollTo(offset()-steps);return true;
    }
    boolean dragScrollbar(double mouseY){
        if(!dragging)return false;
        if(!selecting()||state.get().total()<=SongPanelMenu.VISIBLE_SONGS){dragging=false;return true;}
        double track=(mouseY-top)/zoom-LIST_TOP-grabOffset;
        scrollTo((int)Math.round(Math.clamp(track/(176-thumbHeight()),0,1)*Math.max(0,state.get().total()-3)));return true;
    }
    boolean releaseScrollbar(){boolean was=dragging;dragging=false;return was;}
    private int thumbHeight(){return Math.min(176,Math.max(20,176*SongPanelMenu.VISIBLE_SONGS/Math.max(1,state.get().total())));}
    private int thumbTop(){return (int)Math.round((176-thumbHeight())*(double)offset()/Math.max(1,state.get().total()-SongPanelMenu.VISIBLE_SONGS));}
    void init(int left,int top,float zoom,Consumer<Button> add,IntConsumer request,Runnable close,Runnable rebuild){
        this.request=request;this.rebuild=rebuild;this.left=left;this.top=top;this.zoom=zoom;buttons.clear();
        if(lastGenreId!=state.get().genreId()){lastGenreId=state.get().genreId();targetOffset=-1;wheelRemainder=0;dragging=false;}
        if(targetOffset==state.get().offset())targetOffset=-1;
        queue=new MusicQueueView(()->state.get().queue(),font,true);
        if(selectedSlot<0){queue.init(left,top,zoom,add,request,close,this::select);return;}
        button(left,top,zoom,add,406,8,18,18,"关闭窗口",1,0,()->true,close,"关闭窗口");
        button(left,top,zoom,add,12,260,58,18,"CANCEL",0,0,()->true,this::back,"返回三曲队列");
        initGenres(add);
        scrollbar=new Scrollbar(left+Math.round(412*zoom),top+Math.round(LIST_TOP*zoom),Math.max(1,Math.round(8*zoom)),Math.round(LIST_HEIGHT*zoom));add.accept(scrollbar);
        var songs=state.get().songs();
        for(int i=0;i<songs.size();i++){
            var song=songs.get(i);int y=LIST_TOP+i*60;
            int count=Math.min(6,song.difficulties().size()),badgeWidth=Math.min(32,(194-Math.max(0,count-1)*4)/Math.max(1,count));
            int badgeLeft=202+(194-(count*badgeWidth+Math.max(0,count-1)*4))/2;
            for(int d=0;d<count;d++){
                var difficulty=song.difficulties().get(d);
                String text=difficulty.constant(),tip=difficulty.name()+(text.isBlank()?"": " "+text);
                button(left,top,zoom,add,badgeLeft+d*(badgeWidth+4),y+18,badgeWidth,20,text,0,color(difficulty.name()),
                    ()->state.get().queue().canSelect()&&difficulty.available(),()->{
                        int action=SongPanelMenu.selectAction(selectedSlot,song.songId(),difficulty.name());request.accept(action);back();
                    },tip+(difficulty.available()?"":text.isBlank()?"\n此曲目没有该难度":"\n谱面尚未导入，暂不可选"));
            }
            var info=new Button(left+Math.round(69*zoom),top+Math.round((y+3)*zoom),Math.round(126*zoom),Math.round(50*zoom),
                Component.literal(song.title()+"\n"+song.artist()),button->{},supplier->supplier.get()){
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){if(isFocused())g.outline(getX(),getY(),getWidth(),getHeight(),0xffd96cff);}
            };
            info.setTooltip(Tooltip.create(Component.literal(song.title()+"\n"+song.artist())));info.setTooltipDelay(java.time.Duration.ofMillis(350));add.accept(info);
        }
    }
    private void initGenres(Consumer<Button> add){
        var genres=new ArrayList<SongLibrary.Genre>();genres.add(new SongLibrary.Genre(SongPanelMenu.ALL_GENRES,"ALL"));genres.addAll(state.get().genres());
        int x=0;
        for(var genre:genres){
            int w=Math.max(36,(int)Math.ceil(font.width(Component.literal(genre.name()).withStyle(s->s.withBold(true)))*.8)+14);
            var tab=button(left,top,zoom,add,TAB_LEFT+x,39,w,20,genre.name(),4,0,()->true,()->{
                if(state.get().genreId()!=genre.id()){
                    dragging=false;targetOffset=-1;wheelRemainder=0;request.accept(SongPanelMenu.genreAction(genre.id()));
                }
            },genre.name());
            tab.contentX=x;tab.selected=()->state.get().genreId()==genre.id();x+=w+4;
        }
        tabMax=Math.max(0,x-4-(TAB_RIGHT-TAB_LEFT));
        tabScroll=Math.clamp(tabScroll,0,tabMax);tabTarget=Math.clamp(tabTarget,0,tabMax);tabFrom=Math.clamp(tabFrom,0,tabMax);
        button(left,top,zoom,add,12,39,18,20,"",2,0,()->tabTarget>0,()->scrollGenres(-120),"向左滚动分类");
        button(left,top,zoom,add,402,39,18,20,"",3,0,()->tabTarget<tabMax,()->scrollGenres(120),"向右滚动分类");
        updateGenreLayout();
    }
    private void scrollGenres(double amount){
        updateGenreLayout();tabFrom=tabScroll;tabTarget=Math.clamp(tabTarget+amount,0,tabMax);tabAnimationStart=System.nanoTime();
    }
    private void updateGenreLayout(){
        double t=Math.clamp((System.nanoTime()-tabAnimationStart)/180_000_000.0,0,1);
        tabScroll=tabFrom+(tabTarget-tabFrom)*(1-Math.pow(1-t,3));
        int clipLeft=left+Math.round(TAB_LEFT*zoom),clipRight=left+Math.round(TAB_RIGHT*zoom);
        for(var tab:buttons)if(tab.icon==4){
            int start=left+(int)Math.round((TAB_LEFT+tab.contentX-tabScroll)*zoom);
            int end=left+(int)Math.round((TAB_LEFT+tab.contentX-tabScroll+tab.logicalW)*zoom);
            // Native widget bounds cover only the visible slice. Hidden text cannot capture clicks or tooltips.
            tab.setX(Math.max(clipLeft,start));tab.setWidth(Math.max(0,Math.min(clipRight,end)-tab.getX()));
            tab.visible=tab.getWidth()>0;
        }
    }
    void draw(GuiGraphicsExtractor g){
        if(selectedSlot<0){queue.draw(g);return;}
        updateGenreLayout();
        queue.frame(g,"MUSIC / SELECT · TRACK "+(selectedSlot+1),height());
        queue.panel(g,12,36,408,26);
        var view=state.get();
        for(int i=0;i<view.songs().size();i++){
            var song=view.songs().get(i);int y=LIST_TOP+i*60;
            queue.panel(g,12,y,396,56);g.fill(15,y+4,17,y+52,0xff9d4abf);
            g.fill(20,y+6,64,y+50,0xff151515);
            if(!song.texture().isEmpty())g.blit(RenderPipelines.GUI_TEXTURED,Identifier.parse(song.texture()),21,y+7,0,0,42,42,300,300,300,300);
            queue.text(g,song.title(),70,y+10,124,1.12f,true,0xfff5f5f5);
            queue.text(g,song.artist(),70,y+34,124,.8f,false,0xffb7b7b7);
        }
        if(view.songs().isEmpty())queue.inkCenter(g,view.queue().connected()?"暂无歌曲":"正在读取歌曲列表…",216,150,false,0xff555555,1);
        if(!view.queue().canSelect())queue.text(g,view.queue().writable()?"播放中，选曲已锁定":"当前为只读模式",12,249,390,.8f,false,0xffffd74f);
        queue.inkCenter(g,view.total()==0?"0 / 0":(view.offset()+1)+"–"+(view.offset()+view.songs().size())+" / "+view.total(),216,269,false,0xff303030,.85f);
        scrollbar.active=view.total()>SongPanelMenu.VISIBLE_SONGS;
        for(var button:buttons)button.active=button.enabled.getAsBoolean();
    }
    private static int color(String difficulty){return switch(difficulty){
        case "MASTER"->0xff8a38b5;case "EXPERT"->0xffb83b4b;case "ULTIMA"->ULTIMA_COLOR;
        case "BASIC"->0xff3f9861;case "ADVANCED"->0xffb98528;default->0xff527e9b;
    };}
    private ActionButton button(int left,int top,float zoom,Consumer<Button> add,int x,int y,int w,int h,String label,int icon,int color,
            BooleanSupplier enabled,Runnable action,String tip){
        var button=new ActionButton(left+Math.round(x*zoom),top+Math.round(y*zoom),Math.round(w*zoom),Math.round(h*zoom),w,h,label,icon,color,enabled,action,tip);
        buttons.add(button);add.accept(button);return button;
    }
    private final class ActionButton extends Button {
        final int logicalW,logicalH,icon,color;final BooleanSupplier enabled;final Runnable action;final String label;
        private long pressedUntil;
        int contentX;
        BooleanSupplier selected=()->false;
        ActionButton(int x,int y,int w,int h,int lw,int lh,String label,int icon,int color,BooleanSupplier enabled,Runnable action,String tip){
            super(x,y,w,h,Component.literal(label),button->{},DEFAULT_NARRATION);
            logicalW=lw;logicalH=lh;this.icon=icon;this.color=color;this.enabled=enabled;this.action=action;this.label=label;active=enabled.getAsBoolean();
            if(color!=0)setMessage(Component.literal(tip));
            setTooltip(Tooltip.create(Component.literal(tip)));setTooltipDelay(java.time.Duration.ofMillis(350));
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers input){if(active){pressedUntil=System.nanoTime()+100_000_000L;action.run();}}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            boolean hovered=active&&isHoveredOrFocused(),pressed=active&&System.nanoTime()<pressedUntil;
            if(icon==4)g.enableScissor(left+Math.round(TAB_LEFT*zoom),top+Math.round(39*zoom),left+Math.round(TAB_RIGHT*zoom),top+Math.round(59*zoom));
            g.pose().pushMatrix();
            if(icon==4){g.pose().translate((float)(left+(TAB_LEFT+contentX-tabScroll)*zoom),getY());g.pose().scale(zoom,(float)getHeight()/logicalH);}
            else {g.pose().translate(getX(),getY());g.pose().scale((float)getWidth()/logicalW,(float)getHeight()/logicalH);}
            int face=active?(color!=0?color:hovered?0xffa7a7a7:0xff858585):color!=0&&state.get().queue().canSelect()?dim(color,.48f):0xff666666;
            if(selected.getAsBoolean())face=hovered?0xff804795:0xff603474;
            if(color!=0){
                g.fill(0,0,logicalW,logicalH,0xfff5f5f5);g.fill(1,1,logicalW-1,logicalH-1,face);
                if(color==ULTIMA_COLOR&&state.get().queue().canSelect()){
                    g.fill(1,1,logicalW-1,logicalH-1,0xff17101e);
                    g.fill(1,logicalH-6,logicalW-1,logicalH-1,0xff352042);
                    g.fill(1,logicalH-3,logicalW-1,logicalH-1,0xff603078);
                }
            }
            else HeadControlScreen.bevel(g,0,0,logicalW,logicalH,face,pressed);
            if(selected.getAsBoolean())g.fill(2,logicalH-3,logicalW-2,logicalH-1,0xffffd74f);
            int ink=active?0xfff5f5f5:0xffaaaaaa;
            if(icon==1)for(int i=0;i<8;i++){g.fill(5+i,5+i,7+i,7+i,ink);g.fill(11-i,5+i,13-i,7+i,ink);}
            else if(icon==2||icon==3)for(int i=0;i<5;i++){
                int x=icon==2?7+i:11-i;g.fill(x,logicalH/2-i,x+1,logicalH/2+i+1,ink);
            }
            else {
                if(!label.isEmpty()){
                    Component text=Component.literal(label).withStyle(style->style.withBold(true));
                    float scale=color==0?.8f:Math.min(1.05f,(logicalW-6f)/font.width(text));
                    queue.inkCenter(g,label,logicalW/2f,logicalH/2f,true,ink,scale);
                }
            }
            if(hovered)g.outline(color==0?0:-1,color==0?0:-1,logicalW+(color==0?0:2),logicalH+(color==0?0:2),color==0?0xffd96cff:0xfff5f5f5);
            g.pose().popMatrix();
            if(icon==4)g.disableScissor();
        }
    }
    private static int dim(int color,float amount){return 0xff000000|((int)(((color>>16)&255)*amount)<<16)|((int)(((color>>8)&255)*amount)<<8)|(int)((color&255)*amount);}
    private final class Scrollbar extends Button {
        Scrollbar(int x,int y,int w,int h){
            super(x,y,w,h,Component.literal("歌曲列表滚动条"),button->{},DEFAULT_NARRATION);
            active=state.get().total()>SongPanelMenu.VISIBLE_SONGS;setTooltip(Tooltip.create(Component.literal("滚轮上下浏览，或拖动滚动条")));
        }
        @Override public void onClick(MouseButtonEvent event,boolean doubleClick){
            double cursor=(event.y()-top)/zoom-LIST_TOP;int thumb=thumbTop();
            grabOffset=cursor>=thumb&&cursor<thumb+thumbHeight()?cursor-thumb:thumbHeight()/2.0;dragging=true;dragScrollbar(event.y());
        }
        @Override protected void onDrag(MouseButtonEvent event,double dx,double dy){dragScrollbar(event.y());}
        @Override public void onRelease(MouseButtonEvent event){releaseScrollbar();}
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers input){scrollTo(offset()+1);}
        @Override public boolean keyPressed(KeyEvent event){
            int next=switch(event.key()){case KEY_UP->offset()-1;case KEY_DOWN->offset()+1;case KEY_PAGEUP->offset()-3;
                case KEY_PAGEDOWN->offset()+3;case KEY_HOME->0;case KEY_END->Math.max(0,state.get().total()-3);default->Integer.MIN_VALUE;};
            if(isFocused()&&active&&next!=Integer.MIN_VALUE){scrollTo(next);return true;}return super.keyPressed(event);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            g.pose().pushMatrix();g.pose().translate(getX(),getY());g.pose().scale((float)getWidth()/8,(float)getHeight()/176);
            g.fill(0,0,8,176,0xff656565);g.fill(1,1,7,175,0xff303030);
            HeadControlScreen.bevel(g,0,thumbTop(),8,thumbHeight(),active?0xffb8b8b8:0xff777777,false);
            if(isFocused())g.outline(0,thumbTop(),8,thumbHeight(),0xfff5f5f5);g.pose().popMatrix();
        }
    }
}
