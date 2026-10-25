package io.github.marconik.minecraftchunithm.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.marconik.minecraftchunithm.MusicQueue;
import io.github.marconik.minecraftchunithm.PlayerPanelMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Four-block account ID lookup and player summary for the rear player terminal. */
public final class PlayerPanelScreen extends AbstractContainerScreen<PlayerPanelMenu> {
    private static final int W=432,H=256,GOLD=0xffffd74f,WHITE=0xfff5f5f5,MUTED=0xffb7b7b7;
    private final List<EditBox> blocks=new ArrayList<>();
    private MusicQueueView draw;
    private float zoom;
    private boolean syncing;
    private String knownProfileId;
    public PlayerPanelScreen(PlayerPanelMenu menu,Inventory inventory,Component title){super(menu,inventory,title,W,H);}

    @Override protected void init(){
        super.init();blocks.clear();draw=new MusicQueueView(()->MusicQueue.View.empty(),font,true);
        zoom=Math.max(.1f,Math.min(1.2f,Math.min((width-12f)/W,(height-12f)/H)));
        leftPos=(width-Math.round(W*zoom))/2;topPos=(height-Math.round(H*zoom))/2;
        addRenderableWidget(new IconButton(406,8,18,18,2,"Close window",this::onClose));
        addRenderableWidget(new IconButton(20,46,24,20,0,"Search player ID",this::search));
        addRenderableWidget(new IconButton(376,46,28,20,1,"Log out",this::logout));
        int[] xs={55,135,215,295};
        for(int i=0;i<4;i++){
            // Borderless edit boxes use their Y coordinate as the text baseline origin;
            // place that origin explicitly so the 9 px Minecraft glyphs sit in the
            // vertical centre of the 21 px recessed ID wells.
            var field=new EditBox(font,px(xs[i]),py(52),Math.max(1,Math.round(64*zoom)),Math.max(1,Math.round(10*zoom)),Component.literal("Player ID block "+(i+1)));
            field.setMaxLength(4);field.setCentered(true);field.setBordered(false);field.setTextColor(WHITE);field.setTextColorUneditable(0xff777777);
            field.setTextShadow(false);
            blocks.add(field);addRenderableWidget(field);
        }
        for(int i=0;i<4;i++){
            final int index=i;blocks.get(i).setResponder(value->filter(index,value));
        }
        blocks.get(0).setFocused(true);
        if(minecraft.gameMode!=null&&minecraft.player!=null&&minecraft.player.containerMenu==menu)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);
        syncState();
    }
    private int px(int x){return leftPos+Math.round(x*zoom);}
    private int py(int y){return topPos+Math.round(y*zoom);}
    private void filter(int index,String value){
        if(syncing)return;String digits=value.replaceAll("\\D","");if(digits.length()>4)digits=digits.substring(0,4);
        if(!digits.equals(value)){syncing=true;blocks.get(index).setValue(digits);syncing=false;}
        if(digits.length()==4&&index<3&&blocks.get(index).isFocused()){blocks.get(index).setFocused(false);blocks.get(index+1).setFocused(true);}
    }
    private String input(){var text=new StringBuilder(16);for(var block:blocks)text.append(block.getValue());return text.toString();}
    private void setInput(String id){
        syncing=true;for(int i=0;i<4;i++)blocks.get(i).setValue(id.length()==16?id.substring(i*4,i*4+4):"");syncing=false;
    }
    private void search(){String id=input();if(id.length()==16)ClientPlayNetworking.send(new PlayerPanelMenu.Login(menu.containerId,id));}
    private void logout(){setInput("");knownProfileId="";ClientPlayNetworking.send(new PlayerPanelMenu.Login(menu.containerId,""));blocks.get(0).setFocused(true);}
    private void syncState(){
        String id=menu.state().profile()==null?"":menu.state().profile().id();
        if(knownProfileId==null){knownProfileId=id;if(!id.isEmpty())setInput(id);return;}
        if(!id.equals(knownProfileId)){knownProfileId=id;if(!id.isEmpty())setInput(id);}
    }
    @Override protected void containerTick(){super.containerTick();syncState();}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.key()==InputConstants.KEY_RETURN||event.key()==InputConstants.KEY_NUMPADENTER){search();return true;}
        if(event.key()==InputConstants.KEY_BACKSPACE){
            for(int i=1;i<blocks.size();i++)if(blocks.get(i).isFocused()&&blocks.get(i).getValue().isEmpty()){
                blocks.get(i).setFocused(false);blocks.get(i-1).setFocused(true);blocks.get(i-1).moveCursorToEnd(false);break;
            }
        }
        return super.keyPressed(event);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){}
    @Override public void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.pose().pushMatrix();g.pose().translate(leftPos,topPos);g.pose().scale(zoom,zoom);drawScreen(g);g.pose().popMatrix();
        super.extractContents(g,mx,my,delta);
    }
    private void drawScreen(GuiGraphicsExtractor g){
        HeadControlScreen.bevel(g,0,0,W,H,0xffc6c6c6,false);
        g.fill(5,5,427,29,0xff565656);g.fill(5,29,427,31,0xff777777);g.fill(5,5,7,29,GOLD);
        draw.text(g,"PLAYER / ACCOUNT",12,12,350,1.08f,true,WHITE);
        draw.panel(g,12,38,408,36);g.fill(15,41,17,71,GOLD);
        for(int x:new int[]{55,135,215,295}){g.fill(x,46,x+64,67,0xff161616);g.outline(x,46,64,21,0xff727272);g.fill(x+1,47,x+63,49,0xff303030);}
        // Geometric separators avoid the font glyph's asymmetric bearings and
        // remain centred between the four ID groups at every UI scale.
        for(int x:new int[]{127,207,287})g.fill(x-4,56,x+4,58,MUTED);
        var p=menu.state().profile();if(p==null)return;
        draw.panel(g,12,82,408,162);g.fill(15,85,18,241,GOLD);
        g.fill(24,101,108,185,0xff161616);g.outline(24,101,84,84,0xff777777);
        g.blit(RenderPipelines.GUI_TEXTURED,Identifier.parse(p.texture()),26,103,0,0,80,80,128,128,128,128);
        draw.text(g,p.name(),120,96,177,1.55f,true,WHITE);
        if(!p.team().isEmpty()){
            draw.text(g,"TEAM",310,94,96,.72f,true,MUTED);
            draw.text(g,p.team(),310,107,96,1.02f,true,GOLD);
        }
        if(!p.title().isEmpty()){
            int titleColor=p.titleColor().equals("GOLD")?0xffa88422:0xff777777;
            g.fill(120,119,298,129,0xff1b1b1b);g.outline(120,119,178,10,titleColor);
            draw.inkCenter(g,p.title(),209,124,true,p.titleColor().equals("GOLD")?GOLD:WHITE,.9f);
        }
        int y=151;label(g,"LEVEL",120,y,p.level());label(g,"RATING",120,y+24,p.rating());label(g,"COMPLETION",120,y+48,p.completion()+"%");
        draw.text(g,"PLAYER ID",24,228,68,.65f,true,MUTED);draw.text(g,hyphenated(p.id()),90,226,250,.82f,true,0xffdedede);
    }
    private void label(GuiGraphicsExtractor g,String label,int x,int y,String value){
        draw.text(g,label,x,y,104,.85f,true,MUTED);draw.text(g,value,x+116,y-1,68,1.12f,true,WHITE);
    }
    private static String hyphenated(String id){return id.substring(0,4)+"-"+id.substring(4,8)+"-"+id.substring(8,12)+"-"+id.substring(12);}

    private final class IconButton extends Button {
        final int logicalW,logicalH,icon;final Runnable action;private long pressedUntil;
        IconButton(int x,int y,int w,int h,int icon,String tip,Runnable action){
            super(px(x),py(y),Math.max(1,Math.round(w*zoom)),Math.max(1,Math.round(h*zoom)),Component.literal(tip),button->{},DEFAULT_NARRATION);
            logicalW=w;logicalH=h;this.icon=icon;this.action=action;setTooltip(Tooltip.create(Component.literal(tip)));setTooltipDelay(java.time.Duration.ofMillis(350));
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers input){if(active){pressedUntil=System.nanoTime()+100_000_000L;action.run();}}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            boolean pressed=System.nanoTime()<pressedUntil,hover=active&&isHoveredOrFocused();
            g.pose().pushMatrix();g.pose().translate(getX(),getY());g.pose().scale((float)getWidth()/logicalW,(float)getHeight()/logicalH);
            HeadControlScreen.bevel(g,0,0,logicalW,logicalH,active?(pressed?0xff777777:hover?0xffaaaaaa:0xff858585):0xff686868,pressed);
            int ink=active?(icon==1?0xffff5555:WHITE):(icon==1?0xff793737:0xff888888);
            if(icon==0){
                // The creative-inventory search tab uses a compass; reuse the
                // vanilla item so this control reads immediately as Minecraft search.
                g.fakeItem(new ItemStack(Items.COMPASS),4,2);
            }else{
                int side=Math.min(12,Math.min(logicalW-6,logicalH-6));
                int sx=(logicalW-side)/2,sy=(logicalH-side)/2;
                for(int i=0;i<side;i++){
                    g.fill(sx+i,sy+i,sx+i+2,sy+i+2,ink);
                    g.fill(sx+side-1-i,sy+i,sx+side+1-i,sy+i+2,ink);
                }
            }
            if(hover)g.outline(0,0,logicalW,logicalH,GOLD);g.pose().popMatrix();
        }
    }
}
