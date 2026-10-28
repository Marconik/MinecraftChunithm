package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.SongPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Song panel queue/selector without the head's SPEED or MIRROR controls. */
public final class SongPanelScreen extends AbstractContainerScreen<SongPanelMenu> {
    private SongPanelView view;
    private float zoom;
    private Object catalog;
    public SongPanelScreen(SongPanelMenu menu,Inventory inventory,Component title){super(menu,inventory,title,432,286);}
    @Override protected void init(){
        super.init();if(view==null)view=new SongPanelView(menu::state,font);
        int logicalHeight=view.height();zoom=Math.max(.1f,Math.min(1.2f,Math.min((width-12f)/432,(height-12f)/logicalHeight)));
        leftPos=(width-Math.round(432*zoom))/2;topPos=(height-Math.round(logicalHeight*zoom))/2;
        view.init(leftPos,topPos,zoom,button->addRenderableWidget(button),this::request,()->super.onClose(),this::rebuildWidgets);
        catalog=view.catalog();request(SongPanelMenu.REFRESH);
    }
    private void request(int action){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void containerTick(){super.containerTick();if(!view.catalog().equals(catalog))rebuildWidgets();}
    @Override public void onClose(){if(view!=null&&view.selecting())view.back();else super.onClose();}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){return view.scroll(mx,my,vertical)||super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){return view.dragScrollbar(event.y())||super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){boolean scrolling=view.releaseScrollbar();return super.mouseReleased(event)||scrolling;}
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){}
    @Override public void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.pose().pushMatrix();g.pose().translate(leftPos,topPos);g.pose().scale(zoom,zoom);view.draw(g);g.pose().popMatrix();
        super.extractContents(g,mx,my,delta);
    }
}
