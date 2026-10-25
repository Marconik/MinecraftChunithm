package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;

/** Delegates ordinary TextDisplays; only marked song metadata uses the clipped YaHei viewport. */
public final class SongPanelTextRenderer extends DisplayRenderer.TextDisplayRenderer {
    private record Key(String text,boolean title){}
    private static final int WIDTH=1024,HEIGHT=128;
    private static final Map<Key,Viewport> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static final Map<TextDisplayEntityRenderState,Key> METADATA=new WeakHashMap<>();
    private static final Font REGULAR=loadFont("msyh.ttc",Font.PLAIN),BOLD=loadFont("msyhbd.ttc",Font.BOLD);
    private static int nextTexture;
    static void verifyMarquee(){
        for(boolean title:new boolean[]{true,false}){
            var viewport=CACHE.values().stream().filter(v->v.key.title==title&&v.key.text.contains("Preview")).findFirst().orElseThrow();
            if(viewport.width<=WIDTH||!viewport.moved||viewport.previousOffset>viewport.width-WIDTH+4)
                throw new AssertionError("Long YaHei metadata must scroll inside its viewport");
        }
        MinecraftChunithm.LOGGER.info("YaHei native check passed: song/artist viewports scroll with clipped bounds");
    }
    public SongPanelTextRenderer(EntityRendererProvider.Context context){super(context);}
    public static void initialize(){
        EntityRendererRegistry.register(EntityTypes.TEXT_DISPLAY,SongPanelTextRenderer::new);
        Object[] world={null};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(world[0]!=client.level){
                for(var view:CACHE.values())client.getTextureManager().release(view.id);
                CACHE.clear();METADATA.clear();world[0]=client.level;
            }
            // Limit unused text textures; release between render frames, not during submission.
            var entries=CACHE.entrySet().iterator();
            while(CACHE.size()>64&&entries.hasNext()){
                var view=entries.next().getValue();client.getTextureManager().release(view.id);entries.remove();
            }
        });
    }
    private static Font loadFont(String file,int style){
        try{
            var windows=System.getenv("WINDIR");
            if(windows!=null){
                var path=Path.of(windows,"Fonts",file);
                if(Files.isRegularFile(path)){
                    var font=Font.createFonts(path.toFile())[0];
                    MinecraftChunithm.LOGGER.info("Song panel font: {}",font.getFontName(Locale.ROOT));
                    return font;
                }
            }
        }catch(Exception error){MinecraftChunithm.LOGGER.warn("Cannot read local YaHei font {}",file,error);}
        for(var family:GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            if(family.equalsIgnoreCase("Microsoft YaHei")||family.equals("微软雅黑"))return new Font(family,style,72);
        return new Font(Font.SANS_SERIF,style,72);
    }
    @Override public void extractRenderState(Display.TextDisplay entity,TextDisplayEntityRenderState state,float partialTicks){
        super.extractRenderState(entity,state,partialTicks);
        String marker=entity.getText().getStyle().getInsertion();
        if(SongPanel.TITLE_TEXT.equals(marker)||SongPanel.ARTIST_TEXT.equals(marker))
            METADATA.put(state,new Key(entity.getText().getString(),SongPanel.TITLE_TEXT.equals(marker)));
        else METADATA.remove(state);
    }
    @Override public void submitInner(TextDisplayEntityRenderState state,PoseStack poses,SubmitNodeCollector collector,int light,float progress){
        var key=METADATA.get(state);
        if(key==null){super.submitInner(state,poses,collector,light,progress);return;}
        var view=CACHE.computeIfAbsent(key,Viewport::new);view.update();
        float halfWidth=2.14f,halfHeight=key.title?.22f:.16f;
        collector.submitCustomGeometry(poses,RenderTypes.entityTranslucent(view.id),(pose,vertices)->{
            vertices.addVertex(pose,-halfWidth,-halfHeight,0).setColor(-1).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
            vertices.addVertex(pose,halfWidth,-halfHeight,0).setColor(-1).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
            vertices.addVertex(pose,halfWidth,halfHeight,0).setColor(-1).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
            vertices.addVertex(pose,-halfWidth,halfHeight,0).setColor(-1).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
        });
    }
    private static final class Viewport {
        final Key key;final Identifier id;
        final BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        final Font font;final int width,baseline;
        final DynamicTexture texture;final long created=System.nanoTime();
        int previousOffset=-1;long previousFrame=-1;boolean moved;
        Viewport(Key key){
            this.key=key;
            // The world-space viewports have different aspect ratios. Compensate
            // in raster space so the physical font keeps its original proportions.
            double aspect=(key.title?.44:.32)*WIDTH/(4.28*HEIGHT);
            font=(key.title?BOLD:REGULAR).deriveFont(key.title?84f:68f)
                    .deriveFont(java.awt.geom.AffineTransform.getScaleInstance(aspect,1));
            var graphics=image.createGraphics();graphics.setFont(font);
            var metrics=graphics.getFontMetrics();width=metrics.stringWidth(key.text);baseline=(HEIGHT-metrics.getHeight())/2+metrics.getAscent();graphics.dispose();
            id=Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"runtime/song_text_"+nextTexture++);
            texture=new DynamicTexture("Song panel YaHei viewport",WIDTH,HEIGHT,true);
            Minecraft.getInstance().getTextureManager().register(id,texture);
        }
        void update(){
            long now=System.nanoTime(),frame=now/33_333_333;
            if(frame==previousFrame)return;
            int offset=(int)Math.round(PanelMarquee.offset((now-created)/1_000_000_000.0,Math.max(0,width-WIDTH+4)));
            moved|=offset>0;
            previousFrame=frame;if(offset==previousOffset)return;previousOffset=offset;
            var graphics=image.createGraphics();graphics.setComposite(AlphaComposite.Clear);graphics.fillRect(0,0,WIDTH,HEIGHT);
            graphics.setComposite(AlphaComposite.SrcOver);graphics.setFont(font);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(key.title?0x15151b:0x30303a));
            graphics.drawString(key.text,2-offset,baseline);graphics.dispose();
            var pixels=texture.getPixels();
            for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)pixels.setPixel(x,y,image.getRGB(x,y));
            texture.upload();
        }
    }
}
