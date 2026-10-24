package io.github.marconik.minecraftchunithm;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/** A slot-free menu: vanilla menu-data/button packets carry only server-authoritative state. */
public final class HeadControlMenu extends AbstractContainerMenu {
    public static MenuType<HeadControlMenu> TYPE;
    public static final int TRACK=0,MUSIC=1,PLAYER=2,AIR_LEFT=3,AIR_RIGHT=4,GROUND=5,
            LEFT_LEVEL=6,RIGHT_LEVEL=7,GROUND_LEVEL=8,PLAYBACK=24,START=25,PAUSE=26,STOP=27,PERMISSION=28,READY=29,COUNT=30;
    public static final int OPEN_MUSIC=3,OPEN_PLAYER=4,REFRESH=5;
    public static final int SPEED_UP=6,SPEED_DOWN=7,MIRROR=8,CHOICE_FIRST=9,CHOICE_LAST=14;
    private final ContainerData data;
    private final ServerLevel level;
    private final PartConnections.Part head;
    private final ServerPlayer owner;
    private long lastUpdate=Long.MIN_VALUE,lastStructure=Long.MIN_VALUE,lastAction=Long.MIN_VALUE;
    private boolean providerError;
    private boolean musicWatching;
    private String sentMusic="";
    private MusicQueue.View musicView=MusicQueue.View.empty();
    public static void initialize(){TYPE=Registry.register(BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"head_control"),new MenuType<>(HeadControlMenu::new,FeatureFlags.DEFAULT_FLAGS));}
    public HeadControlMenu(int id,Inventory inventory){this(id,inventory,null,null,null);}
    HeadControlMenu(int id,Inventory inventory,ServerLevel level,PartConnections.Part head,ServerPlayer owner){
        super(TYPE,id);this.level=level;this.head=head;this.owner=owner;
        data=new SimpleContainerData(COUNT);
        for(int i=0;i<3;i++)data.set(i,PartConnections.Status.MISSING.ordinal());
        addDataSlots(data);if(level!=null){prepareMusic();refresh(true);}
    }
    public int value(int key){return data.get(key);}
    public MusicQueue.View musicView(){return musicView;}
    public void receiveMusic(String json){musicView=MusicQueue.decode(json);}
    private void prepareMusic(){
        MusicQueue.refreshSource(level,head);
        if(!net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(owner.createCommandSourceStack()))return;
        if(HeadControlServices.transport(level,head.placement).ready()||ChartPlayback.busyElsewhere(level,head.placement))return;
        try{MusicQueue.prepare(level,head);}catch(RuntimeException error){MinecraftChunithm.LOGGER.warn("Could not prepare stored music queue",error);}
    }
    private void sendMusic(boolean force){
        if(level==null||!musicWatching)return;
        String json=MusicQueue.encode(MusicQueue.snapshot(level,head,value(PERMISSION)!=0));
        if(force||!json.equals(sentMusic)){
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(owner,new MusicQueue.Snapshot(containerId,json));sentMusic=json;
        }
    }
    public PartConnections.Status status(int key){int n=value(key);return n>=0&&n<4?PartConnections.Status.values()[n]:PartConnections.Status.MISSING;}
    public boolean connected(int key){return key<=PLAYER?status(key)==PartConnections.Status.CONNECTED:value(key)==1;}
    public boolean enabled(int action){return action>=0&&action<3&&value(START+action)!=0;}
    private void refresh(boolean forceStructure){
        if(level==null)return;
        MusicQueue.refreshSource(level,head);
        if(forceStructure||lastStructure==Long.MIN_VALUE||level.getGameTime()-lastStructure>=5){
            var links=PartConnections.links(level,head);
            data.set(TRACK,links.get(0).status().ordinal());data.set(PLAYER,links.get(3).status().ordinal());data.set(MUSIC,links.get(4).status().ordinal());
            lastStructure=level.getGameTime();
        }
        boolean permission=net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(owner.createCommandSourceStack());
        data.set(PERMISSION,permission?1:0);
        try{
            var input=HeadControlServices.input(level,head.placement);var transport=HeadControlServices.transport(level,head.placement);
            data.set(AIR_LEFT,input.leftAirConnected()?1:0);data.set(AIR_RIGHT,input.rightAirConnected()?1:0);data.set(GROUND,input.groundConnected()?1:0);
            data.set(LEFT_LEVEL,input.leftAirConnected()?(int)(input.leftAir()*1000):0);data.set(RIGHT_LEVEL,input.rightAirConnected()?(int)(input.rightAir()*1000):0);
            double[] ground=input.ground();for(int i=0;i<16;i++)data.set(GROUND_LEVEL+i,input.groundConnected()?(int)(ground[i]*1000):0);
            data.set(PLAYBACK,transport.state().ordinal());data.set(READY,transport.ready()?1:0);
            setTransportButtons(permission,transport);
            providerError=false;
        }catch(RuntimeException error){
            for(int i=AIR_LEFT;i<COUNT;i++)if(i!=PERMISSION)data.set(i,0);
            setTransportButtons(permission,HeadControlServices.Transport.offline());
            if(!providerError)MinecraftChunithm.LOGGER.error("Head UI provider failed",error);providerError=true;
        }
        lastUpdate=level.getGameTime();
        sendMusic(false);
    }
    private void setTransportButtons(boolean permission,HeadControlServices.Transport transport){
        for(int i=0;i<3;i++)data.set(START+i,permission&&connected(TRACK)&&(i!=0||connected(MUSIC))
                &&HeadControlServices.allowed(HeadControlServices.Action.values()[i],transport)?1:0);
    }
    @Override public void broadcastChanges(){if(level!=null&&(lastUpdate==Long.MIN_VALUE||level.getGameTime()-lastUpdate>=2))refresh(false);super.broadcastChanges();}
    @Override public boolean clickMenuButton(Player player,int action){
        if(level==null||player!=owner||action<0||action>CHOICE_LAST||!stillValid(player))return false;
        long now=level.getGameTime();if(lastAction!=Long.MIN_VALUE&&now-lastAction<2)return false;lastAction=now;
        refresh(true);
        if(action==REFRESH){super.broadcastChanges();return true;}
        if(action==OPEN_MUSIC){
            if(!connected(MUSIC))return false;prepareMusic();musicWatching=true;sendMusic(true);return true;
        }
        if(action==OPEN_PLAYER)return false;
        if(action>=SPEED_UP){
            if(value(PERMISSION)==0||!connected(MUSIC))return false;
            try{
                if(action==SPEED_UP||action==SPEED_DOWN)MusicQueue.settings(level,head,head.music.changeSpeed(action==SPEED_UP?1:-1));
                else if(action==MIRROR)MusicQueue.settings(level,head,new MusicQueueData(head.music.speed(),!head.music.mirror(),head.music.slots()));
                else MusicQueue.cycle(level,head,(action-CHOICE_FIRST)/2,(action-CHOICE_FIRST)%2==0?-1:1);
            }catch(RuntimeException invalid){owner.sendSystemMessage(Component.literal(invalid.getMessage()==null?"曲目设置失败":invalid.getMessage()));}
            refresh(true);sendMusic(true);super.broadcastChanges();return true;
        }
        if(!enabled(action))return false;
        try{
            if(action==0&&HeadControlServices.transport(level,head.placement).state()==HeadControlServices.Playback.FINISHED)MusicQueue.prepare(level,head);
            HeadControlServices.execute(level,head.placement,HeadControlServices.Action.values()[action]);
        }
        catch(RuntimeException e){MinecraftChunithm.LOGGER.error("Playback action failed",e);owner.sendSystemMessage(Component.literal("播放控制器执行失败。"));}
        refresh(true);super.broadcastChanges();return true;
    }
    @Override public boolean stillValid(Player player){return level==null||player==owner&&player.level()==level
            &&player.distanceToSqr(head.placement.point(5.5,2,0))<100
            &&PartConnections.find(level,PartConnections.Kind.HEAD,head.placement)==head
            &&PartConnections.integrity(level,head)==PartConnections.Status.CONNECTED;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ContainerInput input,Player player){} // No inventory interaction.
}
