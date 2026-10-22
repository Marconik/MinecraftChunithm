package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Native Minecraft widgets and pixel geometry, not a flattened concept image or inventory slots. */
public final class HeadControlScreen extends AbstractContainerScreen<HeadControlMenu> {
    private static final int W=432,H=256;
    private static final int GREEN=0xff4df48a,PURPLE=0xffd96cff,GOLD=0xffffd74f;
    private static final int DARK_PANEL=0xff303030;
    private final List<Control> controls=new ArrayList<>();
    private final double[] glow=new double[18];
    private float zoom=1;
    private long lastFrame=System.nanoTime();
    public HeadControlScreen(HeadControlMenu menu,Inventory inventory,Component title){super(menu,inventory,title,W,H);}
    @Override protected void init(){
        super.init();controls.clear();
        zoom=Math.min(1f,Math.min((width-12f)/W,(height-12f)/H));zoom=Math.max(.1f,zoom);
        leftPos=(width-Math.round(W*zoom))/2;topPos=(height-Math.round(H*zoom))/2;
        add(16,36,48,60,"Air",HeadControlMenu.AIR_LEFT,-1,0);
        add(88,36,256,60,"Ground Slide",HeadControlMenu.GROUND,-1,1);
        add(368,36,48,60,"Air",HeadControlMenu.AIR_RIGHT,-1,2);
        add(16,188,104,56,"Music",HeadControlMenu.MUSIC,HeadControlMenu.OPEN_MUSIC,3);
        add(164,200,104,36,"CHUNITHM",-1,HeadControlMenu.REFRESH,4);
        add(312,188,104,56,"Player",HeadControlMenu.PLAYER,HeadControlMenu.OPEN_PLAYER,5);
        add(168,132,28,28,"开始",-2,0,6);
        add(202,132,28,28,"暂停",-2,1,7);
        add(236,132,28,28,"结束",-2,2,8);
        add(406,8,18,18,"关闭",-1,-2,9);
    }
    private void add(int x,int y,int w,int h,String label,int state,int action,int icon){
        var button=new Control(leftPos+Math.round(x*zoom),topPos+Math.round(y*zoom),Math.round(w*zoom),Math.round(h*zoom),w,h,label,state,action,icon);
        controls.add(button);addRenderableWidget(button);
    }
    private void request(int action){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){} // No inventory title or player slots.
    @Override public void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        double elapsed=Math.min(.2,(System.nanoTime()-lastFrame)/1e9);lastFrame=System.nanoTime();
        for(int i=0;i<18;i++){
            boolean live=i<2?menu.connected(i==0?HeadControlMenu.AIR_LEFT:HeadControlMenu.AIR_RIGHT):menu.connected(HeadControlMenu.GROUND);
            int key=i<2?HeadControlMenu.LEFT_LEVEL+i:HeadControlMenu.GROUND_LEVEL+i-2;
            glow[i]=live?Math.max(menu.value(key)/1000.0,glow[i]*Math.exp(-elapsed/0.045)):0;
        }
        g.pose().pushMatrix();g.pose().translate(leftPos,topPos);g.pose().scale(zoom,zoom);
        bevel(g,0,0,W,H,0xffc6c6c6,false);
        g.fill(5,5,W-5,29,0xffd6d6d6);g.fill(5,29,W-5,31,0xff777777);
        Component heading=Component.literal("CHUNITHM / DEVICE CONTROL").withStyle(style->style.withBold(true));
        float headingWidth=Math.min(1.2f,382f/font.width(heading));
        g.pose().pushMatrix();g.pose().translate(12,12);g.pose().scale(headingWidth,1.05f);
        g.text(font,heading,0,0,0xffffffff,true);
        g.pose().popMatrix();
        wire(g,64,66,88,66,menu.connected(HeadControlMenu.AIR_LEFT),GREEN);
        wire(g,344,66,368,66,menu.connected(HeadControlMenu.AIR_RIGHT),GREEN);
        wire(g,216,96,216,108,menu.connected(HeadControlMenu.GROUND)&&menu.connected(HeadControlMenu.TRACK),GOLD);
        wire(g,216,170,216,200,menu.connected(HeadControlMenu.TRACK),GOLD);
        wire(g,120,218,164,218,menu.connected(HeadControlMenu.MUSIC),PURPLE);
        wire(g,268,218,312,218,menu.connected(HeadControlMenu.PLAYER),GOLD);
        boolean trackConnected=menu.connected(HeadControlMenu.TRACK);
        modulePanel(g,156,108,120,62,trackConnected,GOLD,0,false,false);
        centeredLabel(g,"Track",216,115,trackConnected?0xfff5f5f5:0xff656565,trackConnected);
        if(trackConnected)g.outline(157,109,118,60,dim(GOLD,.9));
        else {g.outline(157,109,118,60,0xff777777);g.text(font,"!",263,115,0xff757575,false);}
        g.pose().popMatrix();
        for(var control:controls)control.update();
        super.extractContents(g,mx,my,delta);
    }
    private String reason(int key){
        if(key>=HeadControlMenu.AIR_LEFT)return menu.connected(key)?"已连接 · 实时输入监视":"未接入输入设备";
        return switch(menu.status(key)){
            case CONNECTED->"已连接";case MISSING->"未连接或接口未对齐";case BROKEN->"结构损坏";case UNLOADED->"区块未加载";
        };
    }
    private String playbackReason(int action){
        if(!menu.connected(HeadControlMenu.TRACK))return "轨道："+reason(HeadControlMenu.TRACK);
        if(menu.value(HeadControlMenu.PERMISSION)==0)return "需要管理员权限";
        if(action!=0)return menu.value(HeadControlMenu.READY)==0?"当前无谱面":"点击操作";
        if(!menu.connected(HeadControlMenu.MUSIC))return "歌曲面板未连接";
        if(menu.value(HeadControlMenu.READY)==0)return "暂无可播放谱面";
        if(menu.enabled(action))return "开始 / 继续播放";
        return "正在播放";
    }
    private final class Control extends Button {
        final int logicalW,logicalH,state,action,icon;final String label;
        private String lastTip="";
        private long pressedUntil;
        Control(int x,int y,int w,int h,int lw,int lh,String label,int state,int action,int icon){
            super(x,y,w,h,Component.literal(label),b->{},DEFAULT_NARRATION);
            logicalW=lw;logicalH=lh;this.label=label;this.state=state;this.action=action;this.icon=icon;
            setTooltipDelay(java.time.Duration.ofMillis(350));
        }
        void update(){
            active=state==-2?menu.enabled(action):state<0||menu.connected(state);
            String tip=label+"\n"+(state==-2?playbackReason(action):state>=0?reason(state):icon==4?
                    "轨道左 / 中 / 右："+reason(HeadControlMenu.TRACK)+"\nMusic："+reason(HeadControlMenu.MUSIC)+"\nPlayer："+reason(HeadControlMenu.PLAYER)+"\n点击刷新":"关闭窗口");
            if(!tip.equals(lastTip)){lastTip=tip;setTooltip(Tooltip.create(Component.literal(tip)));setMessage(Component.literal(tip));}
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers input){
            pressedUntil=System.nanoTime()+100_000_000L;
            if(action==-2){onClose();return;}
            // Connected Music/Player cards are visual placeholders for now.
            if(action==HeadControlMenu.OPEN_MUSIC||action==HeadControlMenu.OPEN_PLAYER)return;
            request(action>=0?action:HeadControlMenu.REFRESH);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            g.pose().pushMatrix();g.pose().translate(getX(),getY());g.pose().scale((float)getWidth()/logicalW,(float)getHeight()/logicalH);
            boolean highlight=active&&isHoveredOrFocused();
            boolean pressed=active&&System.nanoTime()<pressedUntil;
            if(icon>=6){
                bevel(g,0,0,logicalW,logicalH,active?(pressed?0xff858585:highlight?0xffbababa:0xff939393):0xffa6a6a6,pressed);
                int color=active?0xfffafafa:0xff707070;
                if(icon==6)circle(g,14,14,6,active?0xff4dd66b:0xff707070);
                if(icon==7){g.fill(8,7,12,21,color);g.fill(17,7,21,21,color);}
                if(icon==8)g.fill(8,8,20,20,active?0xffed4b4b:0xff707070);
                if(icon==9){for(int i=0;i<8;i++){g.fill(5+i,5+i,7+i,7+i,0xff282828);g.fill(11-i,5+i,13-i,7+i,0xff282828);}}
                boolean selected=icon==6&&menu.value(HeadControlMenu.PLAYBACK)==HeadControlServices.Playback.PLAYING.ordinal()
                        ||icon==7&&menu.value(HeadControlMenu.PLAYBACK)==HeadControlServices.Playback.PAUSED.ordinal();
                if(selected)g.outline(1,1,logicalW-2,logicalH-2,GOLD);
            }else{
                int color=accent(icon);
                boolean connected=state<0||menu.connected(state);
                double strength=icon==0?glow[0]:icon==2?glow[1]:0;
                if(icon==1)for(int i=2;i<18;i++)strength=Math.max(strength,glow[i]);
                modulePanel(g,0,0,logicalW,logicalH,connected,color,strength,highlight,pressed);
                int textColor=connected?(icon==4?GOLD:0xfff5f5f5):0xff656565;
                centeredLabel(g,label,logicalW/2,icon==4?13:7,textColor,connected);
                if(icon==0||icon==2){
                    int ink=connected?dim(GREEN,.5+strength*.5):0xff777777;
                    for(int j=0;j<3;j++)for(int i=0;i<8;i++){
                        g.fill(17+i,27+j*8-i/2,19+i,30+j*8-i/2,ink);
                        g.fill(24+i,23+j*8+i/2,26+i,26+j*8+i/2,ink);
                    }
                    for(int j=0;j<4;j++){g.fill(8,25+j*7,11,29+j*7,ink);g.fill(37,25+j*7,40,29+j*7,ink);}
                }
                if(icon==1){
                    int lanes=16,laneWidth=13,gap=2;
                    int groupWidth=lanes*laneWidth+(lanes-1)*gap;
                    int firstX=(logicalW-groupWidth)/2;
                    for(int i=0;i<lanes;i++){
                    int keyColor=java.awt.Color.HSBtoRGB((float)(i*.88/15),.95f,1)|0xff000000;
                    int fill=connected?dim(keyColor,.45+glow[i+2]*.55):0xff858585;
                    int x=firstX+i*(laneWidth+gap);
                    g.fill(x,25,x+laneWidth,52,0xff666666);g.fill(x+1,26,x+laneWidth-1,51,fill);
                    if(connected&&glow[i+2]>.1){
                        g.fill(x,23,x+laneWidth,25,withAlpha(keyColor,100));
                        g.fill(x+1,24,x+laneWidth-1,26,keyColor);
                        g.fill(x+2,26,x+laneWidth-2,27,0xffe9ffff);
                    }
                }}
                if(icon==3){
                    int ink=connected?PURPLE:0xff747474;
                    if(connected)pixelGlow(g,28,32,12,10,PURPLE,.7);
                    g.fill(24,28,43,46,connected?0xff161616:0xff343434);g.fill(20,32,47,42,connected?0xff161616:0xff343434);g.fill(30,34,38,40,ink);
                    g.fill(57,25,61,44,ink);g.fill(57,25,73,29,ink);g.fill(69,25,73,41,ink);g.fill(53,40,61,45,ink);g.fill(65,37,73,42,ink);
                }
                if(icon==5){
                    g.fill(12,23,36,47,0xff5b5b5b);g.fill(15,26,33,44,connected?0xffca9d70:0xff888888);
                    g.fill(15,26,33,31,connected?0xff66452d:0xff686868);g.fill(18,33,21,36,0xffe8e8e8);g.fill(27,33,30,36,0xffe8e8e8);
                    g.fill(45,29,89,34,connected?0xffdddddd:0xff8e8e8e);g.fill(45,40,76,44,connected?0xffdddddd:0xff8e8e8e);
                }
                if(!connected&&state>=0&&state<=HeadControlMenu.PLAYER&&menu.status(state)!=PartConnections.Status.MISSING)
                    g.text(font,menu.status(state)==PartConnections.Status.BROKEN?"!":"…",logicalW-11,7,0xff973b3b,false);
            }
            if(highlight)g.outline(0,0,logicalW,logicalH,icon<6?accent(icon):0xfff8f8f8);
            g.pose().popMatrix();
        }
    }
    private void centeredLabel(GuiGraphicsExtractor g,String text,int x,int y,int color,boolean bold){
        // centeredText() enables a dark offset shadow by default, which doubles
        // dark glyphs on these light panels. Render the label once without it.
        Component label=Component.literal(text).withStyle(style->style.withBold(bold));
        g.text(font,label,x-font.width(label)/2,y,color,false);
    }
    private static void circle(GuiGraphicsExtractor g,int cx,int cy,int radius,int color){
        for(int y=-radius;y<=radius;y++){
            int half=(int)Math.floor(Math.sqrt(radius*radius-y*y));
            g.fill(cx-half,cy+y,cx+half+1,cy+y+1,color);
        }
    }
    private static int dim(int color,double amount){
        int r=(int)(((color>>16)&255)*amount),g=(int)(((color>>8)&255)*amount),b=(int)((color&255)*amount);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    private static int accent(int icon){return icon==0||icon==2?GREEN:icon==3?PURPLE:GOLD;}
    private static int withAlpha(int color,int alpha){return color&0x00ffffff|alpha<<24;}
    private static void pixelGlow(GuiGraphicsExtractor g,int x,int y,int w,int h,int color,double strength){
        // Stepped translucent rings preserve Minecraft's pixel edges; no shader bloom needed.
        double power=.65+.35*strength;
        g.outline(x-3,y-3,w+6,h+6,withAlpha(color,(int)(24*power)));
        g.outline(x-2,y-2,w+4,h+4,withAlpha(color,(int)(48*power)));
        g.outline(x-1,y-1,w+2,h+2,withAlpha(color,(int)(100*power)));
    }
    private static void modulePanel(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean connected,int color,double strength,boolean hovered,boolean pressed){
        if(!connected){bevel(g,x,y,w,h,0xffa5a5a5,true);g.outline(x+1,y+1,w-2,h-2,0xff7e7e7e);return;}
        pixelGlow(g,x,y,w,h,color,hovered?1:strength);
        g.fill(x,y,x+w,y+h,0xff151515);
        g.fill(x+1,y+1,x+w-1,y+h-1,pressed?0xff252525:hovered?0xff383838:DARK_PANEL);
        g.fill(x+2,y+2,x+w-2,y+3,0xff575757);
        g.fill(x+2,y+2,x+3,y+h-2,0xff575757);
        g.fill(x+2,y+h-3,x+w-2,y+h-2,0xff1b1b1b);
        g.fill(x+w-3,y+2,x+w-2,y+h-2,0xff1b1b1b);
        g.outline(x+1,y+1,w-2,h-2,dim(color,hovered?1:.8+.2*strength));
        g.outline(x+3,y+3,w-6,h-6,withAlpha(color,(int)(65+70*strength)));
        g.fill(x+4,y+4,x+w-4,y+5,withAlpha(color,(int)(35+30*strength)));
    }
    private static void wire(GuiGraphicsExtractor g,int x,int y,int x2,int y2,boolean on,int color){
        int a=Math.min(x,x2),b=Math.min(y,y2),w=Math.abs(x2-x)+1,h=Math.abs(y2-y)+1;
        if(on){
            g.fill(a-3,b-3,a+w+3,b+h+3,withAlpha(color,24));
            g.fill(a-2,b-2,a+w+2,b+h+2,withAlpha(color,48));
        }
        g.fill(a-2,b-2,a+w+2,b+h+2,0xff606060);g.fill(a,b,a+w,b+h,on?dim(color,.75):0xff8c8c8c);
        g.fill(x-2,y-2,x+3,y+3,on?color:0xff898989);g.fill(x2-2,y2-2,x2+3,y2+3,on?color:0xff898989);
    }
    private static void bevel(GuiGraphicsExtractor g,int x,int y,int w,int h,int face,boolean inset){
        g.fill(x,y,x+w,y+h,0xff373737);g.fill(x+1,y+1,x+w-1,y+h-1,face);
        int hi=inset?0xff606060:0xffffffff,lo=inset?0xffeeeeee:0xff656565;
        g.fill(x+1,y+1,x+w-1,y+3,hi);g.fill(x+1,y+1,x+3,y+h-1,hi);
        g.fill(x+1,y+h-3,x+w-1,y+h-1,lo);g.fill(x+w-3,y+1,x+w-1,y+h-1,lo);
    }
}
