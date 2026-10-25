package io.github.marconik.minecraftchunithm;

import com.google.gson.Gson;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative player account lookup for the left information panel. */
public final class PlayerPanelMenu extends AbstractContainerMenu {
    public static MenuType<PlayerPanelMenu> TYPE;
    public static final String GUEST_ID="0000000000000000";
    public record ProfileView(String id,String name,String characterId,String texture,String level,String rating,
                              String title,String titleColor,String team,int completion){}
    public record State(boolean writable,ProfileView profile){public static State empty(){return new State(false,null);}}
    public record Snapshot(int containerId,String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"player_panel_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
                (buffer,value)->{buffer.writeVarInt(value.containerId);buffer.writeUtf(value.json,16384);},
                buffer->new Snapshot(buffer.readVarInt(),buffer.readUtf(16384)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Login(int containerId,String id) implements CustomPacketPayload {
        public static final Type<Login> TYPE=new Type<>(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"player_panel_login"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Login> CODEC=StreamCodec.of(
                (buffer,value)->{buffer.writeVarInt(value.containerId);buffer.writeUtf(value.id,16);},
                buffer->new Login(buffer.readVarInt(),buffer.readUtf(16)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private final ServerLevel level;
    private final PartConnections.Part panel;
    private final ServerPlayer owner;
    private State state=State.empty();
    private String sent="";
    private long lastAction=Long.MIN_VALUE,lastUpdate=Long.MIN_VALUE;

    public static void initialize(){
        TYPE=Registry.register(BuiltInRegistries.MENU,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"player_panel"),
                new MenuType<>(PlayerPanelMenu::new,FeatureFlags.DEFAULT_FLAGS));
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Login.TYPE,Login.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Login.TYPE,(payload,context)->context.server().execute(()->{
            var player=context.player();
            if(player.containerMenu instanceof PlayerPanelMenu menu&&menu.containerId==payload.containerId())menu.login(player,payload.id());
        }));
    }
    public PlayerPanelMenu(int id,Inventory inventory){this(id,inventory,null,null,null);}
    PlayerPanelMenu(int id,Inventory inventory,ServerLevel level,PartConnections.Part panel,ServerPlayer owner){
        super(TYPE,id);this.level=level;this.panel=panel;this.owner=owner;if(level!=null)refresh(true);
    }
    public State state(){return state;}
    public void receive(String json){state=decode(json);}
    public static String encode(State state){return new Gson().toJson(state);}
    public static State decode(String json){
        var value=new Gson().fromJson(json,State.class);if(value==null)throw new IllegalArgumentException("Missing player panel state");
        var p=value.profile();if(p!=null&&(!p.id().matches("\\d{16}")||!p.characterId().matches("\\d{4}")||p.completion()<0||p.completion()>100))
            throw new IllegalArgumentException("Invalid player panel state");
        return value;
    }
    // Account sign-in is a player-facing action, not an operator-only panel edit.
    private boolean writable(){return owner!=null;}
    private ProfileView view(String id){
        if(id==null||!id.matches("\\d{16}"))return null;
        if(id.equals(GUEST_ID)){
            var avatar=CharacterLibrary.find(level,"0000");if(avatar==null)return null;
            return new ProfileView(id,"GUEST","0000","minecraftchunithm:textures/character/0000.png","****","*****","","SILVER","",0);
        }
        var profile=PlayerLibrary.find(level,id);if(profile==null)return null;
        var avatar=CharacterLibrary.find(level,profile.characterId());if(avatar==null)return null;
        return new ProfileView(profile.id(),profile.name(),profile.characterId(),"minecraftchunithm:textures/character/"+profile.characterId()+".png",
                Integer.toString(profile.level()),profile.rating().setScale(2).toPlainString(),profile.title(),profile.titleColor().name(),profile.team(),0);
    }
    private NameDisplayData display(ProfileView profile){
        var avatar=CharacterLibrary.find(level,profile.characterId());if(avatar==null)return null;
        return new NameDisplayData(profile.team(),profile.title(),NameDisplayData.TitleColor.valueOf(profile.titleColor()),profile.level(),
                profile.name(),Identifier.parse(avatar.itemModel()),profile.rating());
    }
    private void clear(){
        panel.playerId="";var avatar=CharacterLibrary.find(level,"0000");
        if(avatar!=null)NamePanel.update(level,panel.placement,new NameDisplayData("","",NameDisplayData.TitleColor.SILVER,"**","",
                Identifier.parse(avatar.itemModel()),"**.**"));
        else PartConnections.save(level);
    }
    private void login(ServerPlayer player,String id){
        if(level==null||player!=owner||!stillValid(player)||!writable()||id==null||!id.isEmpty()&&!id.matches("\\d{16}"))return;
        long now=level.getGameTime();if(lastAction!=Long.MIN_VALUE&&now-lastAction<2)return;lastAction=now;
        var profile=id.isEmpty()?null:view(id);
        if(profile==null){clear();refresh(true);return;}
        var display=display(profile);if(display==null){clear();refresh(true);return;}
        panel.playerId=id;NamePanel.update(level,panel.placement,display);refresh(true);
    }
    private void refresh(boolean force){
        if(level==null||owner.containerMenu!=this)return;
        state=new State(writable(),view(panel.playerId));String json=encode(state);
        if(force||!json.equals(sent)){ServerPlayNetworking.send(owner,new Snapshot(containerId,json));sent=json;}
        lastUpdate=level.getGameTime();
    }
    @Override public void broadcastChanges(){if(level!=null&&(lastUpdate==Long.MIN_VALUE||level.getGameTime()-lastUpdate>=5))refresh(false);super.broadcastChanges();}
    @Override public boolean clickMenuButton(Player player,int action){
        if(level==null||player!=owner||action!=0||!stillValid(player))return false;
        refresh(true);return true;
    }
    @Override public boolean stillValid(Player player){return level==null||player==owner&&player.level()==level
            &&player.distanceToSqr(panel.placement.point(3.5,1.5,0))<100
            &&PartConnections.find(level,PartConnections.Kind.PLAYER,panel.placement)==panel
            &&PartConnections.integrity(level,panel)==PartConnections.Status.CONNECTED;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ContainerInput input,Player player){}
}
