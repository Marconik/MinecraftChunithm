package io.github.marconik.minecraftchunithm;

import com.google.gson.Gson;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Slot-free native song panel menu. Scroll windows and selections are server-authoritative. */
public final class SongPanelMenu extends AbstractContainerMenu {
    public static MenuType<SongPanelMenu> TYPE;
    public static final int REFRESH=0,VISIBLE_SONGS=3;
    public static final int ALL_GENRES=-1;
    private static final int GENRE_BASE=24_500_000;
    private static final int SCROLL_BASE=25_000_000;
    private static final int SELECT_BASE=32,SONG_LIMIT=1_000_000,SLOT_STRIDE=SONG_LIMIT*8;
    private static final List<String> DIFFICULTIES=List.of("BASIC","ADVANCED","EXPERT","MASTER","ULTIMA","WORLD'S END");
    public record Selection(int slot,int songId,String difficulty){}
    public record State(MusicQueue.View queue,int offset,int total,List<SongLibrary.Song> songs,int genreId,List<SongLibrary.Genre> genres){
        public static State empty(){return new State(MusicQueue.View.empty(),0,0,List.of(),ALL_GENRES,List.of());}
    }
    public record Snapshot(int containerId,String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"song_panel_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (buffer,value)->{buffer.writeVarInt(value.containerId);buffer.writeUtf(value.json,65536);},
            buffer->new Snapshot(buffer.readVarInt(),buffer.readUtf(65536)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private final ServerLevel level;
    private final PartConnections.Part panel;
    private final ServerPlayer owner;
    private State state=State.empty();
    private int offset,genreId=ALL_GENRES;
    private long lastUpdate=Long.MIN_VALUE,lastAction=Long.MIN_VALUE;
    private String sent="";
    public static void initialize(){
        TYPE=Registry.register(BuiltInRegistries.MENU,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"song_panel"),new MenuType<>(SongPanelMenu::new,FeatureFlags.DEFAULT_FLAGS));
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);
    }
    public SongPanelMenu(int id,Inventory inventory){this(id,inventory,null,null,null);}
    SongPanelMenu(int id,Inventory inventory,ServerLevel level,PartConnections.Part panel,ServerPlayer owner){
        super(TYPE,id);this.level=level;this.panel=panel;this.owner=owner;
    }
    public State state(){return state;}
    public static String encode(State state){return new Gson().toJson(state);}
    public void receive(String json){state=decode(json);}
    public static State decode(String json){
        var next=new Gson().fromJson(json,State.class);
        if(next==null||next.queue()==null||next.queue().rows()==null||next.queue().rows().size()!=3||next.songs()==null||next.total()<0
                ||next.offset()<0||next.offset()!=clampOffset(next.offset(),next.total())||next.songs().size()!=Math.min(VISIBLE_SONGS,next.total())
                ||next.genres()==null||next.genreId()<ALL_GENRES)
            throw new IllegalArgumentException("Invalid song panel state");
        int previous=-1;var names=new HashSet<String>();
        for(var genre:next.genres()){
            if(genre==null||genre.id()<=previous||genre.id()>499_998||genre.name()==null||genre.name().isBlank()||!names.add(genre.name()))
                throw new IllegalArgumentException("Invalid song genre list");
            previous=genre.id();
        }
        if(next.genreId()!=ALL_GENRES&&(next.genres().stream().noneMatch(g->g.id()==next.genreId())
                ||next.songs().stream().anyMatch(song->!song.genreIds().contains(next.genreId()))))
            throw new IllegalArgumentException("Invalid song genre filter");
        return next;
    }
    public static int genreAction(int id){
        if(id<ALL_GENRES||id>499_998)throw new IllegalArgumentException("Invalid genre ID");
        return GENRE_BASE+id+1;
    }
    public static boolean isGenreAction(int action){return action>=GENRE_BASE&&action<SCROLL_BASE;}
    public static int genreSelection(int action){
        if(!isGenreAction(action))throw new IllegalArgumentException("Invalid genre action");
        return action-GENRE_BASE-1;
    }
    public static int selectAction(int slot,int songId,String difficulty){
        int d=DIFFICULTIES.indexOf(difficulty);
        if(slot<0||slot>=3||songId<1||songId>=SONG_LIMIT||d<0)throw new IllegalArgumentException("Invalid song selection");
        return SELECT_BASE+slot*SLOT_STRIDE+songId*8+d;
    }
    public static Selection selection(int action){
        int value=action-SELECT_BASE;
        if(value<0||value>=3*SLOT_STRIDE)throw new IllegalArgumentException("Invalid song selection action");
        int slot=value/SLOT_STRIDE,songId=value%SLOT_STRIDE/8,difficulty=value%8;
        if(songId==0||difficulty>=DIFFICULTIES.size())throw new IllegalArgumentException("Invalid song selection action");
        return new Selection(slot,songId,DIFFICULTIES.get(difficulty));
    }
    public static int clampOffset(int offset,int total){return Math.clamp(offset,0,Math.max(0,total-VISIBLE_SONGS));}
    public static int scrollAction(int offset){
        if(offset<0||offset>Integer.MAX_VALUE-SCROLL_BASE)throw new IllegalArgumentException("Invalid scroll offset");
        return SCROLL_BASE+offset;
    }
    private void refresh(boolean force){
        if(level==null||owner.containerMenu!=this)return;
        boolean permission=net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(owner.createCommandSourceStack());
        var genres=SongLibrary.genres(level);
        if(genreId!=ALL_GENRES&&genres.stream().noneMatch(g->g.id()==genreId)){genreId=ALL_GENRES;offset=0;}
        var catalog=SongLibrary.filter(SongLibrary.catalog(level),genreId);offset=clampOffset(offset,catalog.size());
        var songs=List.copyOf(catalog.subList(offset,Math.min(catalog.size(),offset+VISIBLE_SONGS)));
        state=new State(MusicQueue.panelSnapshot(level,panel,permission),offset,catalog.size(),songs,genreId,genres);
        String json=encode(state);if(force||!json.equals(sent)){ServerPlayNetworking.send(owner,new Snapshot(containerId,json));sent=json;}
        lastUpdate=level.getGameTime();
    }
    @Override public void broadcastChanges(){
        if(level!=null&&(lastUpdate==Long.MIN_VALUE||level.getGameTime()-lastUpdate>=2))refresh(false);
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int action){
        if(level==null||player!=owner||!stillValid(player))return false;
        if(action>=SCROLL_BASE){offset=clampOffset(action-SCROLL_BASE,SongLibrary.filter(SongLibrary.catalog(level),genreId).size());refresh(true);return true;}
        if(isGenreAction(action)){
            int selected=genreSelection(action);
            if(selected!=ALL_GENRES&&SongLibrary.genres(level).stream().noneMatch(g->g.id()==selected))return false;
            genreId=selected;offset=0;refresh(true);return true;
        }
        long now=level.getGameTime();if(lastAction!=Long.MIN_VALUE&&now-lastAction<2)return false;lastAction=now;
        refresh(false);
        if(action==REFRESH){refresh(true);return true;}
        if(!state.queue().canSelect())return false;
        try{
            var selected=selection(action);
            var chart=SongLibrary.find(level,new MusicQueueData.Choice(selected.songId(),selected.difficulty(),false));
            if(chart==null||!Set.of("MASTER","EXPERT","ULTIMA").contains(chart.difficulty()))return false;
            MusicQueue.setPanelChoice(level,panel,selected.slot(),new MusicQueueData.Choice(chart.songId(),chart.difficulty(),false));
        }catch(RuntimeException invalid){CommandFeedback.failure(owner,Component.literal(invalid.getMessage()==null?"选曲失败":invalid.getMessage()));}
        refresh(true);return true;
    }
    @Override public boolean stillValid(Player player){return level==null||player==owner&&player.level()==level
        &&player.distanceToSqr(panel.placement.point(3.5,1.5,0))<100
        &&PartConnections.find(level,PartConnections.Kind.SONG,panel.placement)==panel
        &&PartConnections.integrity(level,panel)==PartConnections.Status.CONNECTED;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ContainerInput input,Player player){}
}
