package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Back-face entry points. Only the player importer still uses inventory-backed widgets. */
final class PartWindows {
    static void initialize(){
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(!(world instanceof ServerLevel level)||!(player instanceof ServerPlayer sp))return InteractionResult.PASS;
            var part=PartConnections.at(level,hit.getBlockPos());
            if(part!=null&&WorldUninstall.running(level.getServer()))return InteractionResult.FAIL;
            if(part==null||hit.getDirection()!=part.placement.forward().getOpposite())return InteractionResult.PASS;
            // Restrict activation to the blocks behind the visible terminal.
            boolean terminal=part.kind==PartConnections.Kind.HEAD?HeadTerminal.contains(part.placement,hit.getLocation()):
                    (part.kind==PartConnections.Kind.PLAYER||part.kind==PartConnections.Kind.SONG)
                    &&HeadTerminal.contains(part.placement,hit.getLocation(),3.5,1.5,PanelTile.width(part.kind==PartConnections.Kind.PLAYER),1);
            if(!terminal)return InteractionResult.PASS;
            if(PartConnections.integrity(level,part)!=PartConnections.Status.CONNECTED){CommandFeedback.failure(sp,Component.literal("部件损坏或区块未加载，请恢复完整结构。"));return InteractionResult.SUCCESS;}
            open(sp,level,part,0);return InteractionResult.SUCCESS;
        });
    }
    private static String status(PartConnections.Status s){return switch(s){case CONNECTED->"已连接";case MISSING->"未连接（位置或朝向不匹配）";case BROKEN->"结构损坏";case UNLOADED->"区块未加载";};}
    static void open(ServerPlayer player,ServerLevel level,PartConnections.Part part,int page){
        open(player,level,part,page,null);
    }
    static void open(ServerPlayer player,ServerLevel level,PartConnections.Part part,int page,PartConnections.Part sourceHead){
        if(part.kind==PartConnections.Kind.HEAD){
            if(!PartPorts.checkReady(player.createCommandSourceStack()))return;
            // Refresh existing registered heads when opening after a resource/model update.
            PartPorts.draw(level,part);
            player.openMenu(new SimpleMenuProvider((id,inventory,p)->new HeadControlMenu(id,inventory,level,part,player),Component.literal("CHUNITHM / DEVICE CONTROL")));
            return;
        }
        if(part.kind==PartConnections.Kind.SONG){
            player.openMenu(new SimpleMenuProvider((id,inventory,p)->new SongPanelMenu(id,inventory,level,part,player),Component.literal("MUSIC / TRACK SELECT")));
            return;
        }
        if(part.kind==PartConnections.Kind.PLAYER){
            player.openMenu(new SimpleMenuProvider((id,inventory,p)->new PlayerPanelMenu(id,inventory,level,part,player),Component.literal("PLAYER / ACCOUNT")));
            return;
        }
        String title=switch(part.kind){case HEAD->"头部 · 部件连接状态";case PLAYER->"玩家面板 · 导入信息";case SONG->"歌曲面板 · 播放设置";default->"部件";};
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new Window(id,inventory,player,level,part,page,new SimpleContainer(27),sourceHead),Component.literal(title)));
    }
    private static final class Window extends ChestMenu {
        final SimpleContainer buttons;final Map<Integer,Runnable> actions=new HashMap<>();
        final ServerPlayer owner;final ServerLevel level;final PartConnections.Part part;
        final PartConnections.Part sourceHead;
        Window(int id,net.minecraft.world.entity.player.Inventory inv,ServerPlayer owner,ServerLevel level,PartConnections.Part part,int page,SimpleContainer container,PartConnections.Part sourceHead){
            super(MenuType.GENERIC_9x3,id,inv,container,3);this.buttons=container;this.owner=owner;this.level=level;this.part=part;this.sourceHead=sourceHead;
            button(26,Items.BARRIER,"关闭",owner::closeContainer);
            if(part.kind==PartConnections.Kind.HEAD){
                return; // Head uses its own native control screen.
            }
        }
        void reopen(int page){open(owner,level,part,page,sourceHead);}
        void button(int slot,Item item,String label,Runnable action){var stack=new ItemStack(item);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal(label));buttons.setItem(slot,stack);actions.put(slot,action);}
        @Override public void clicked(int slot,int button,ContainerInput input,Player player){
            if(player!=owner||!stillValid(player))return;
            if(slot==26){owner.closeContainer();return;}
            if(part.kind!=PartConnections.Kind.HEAD&&!net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(owner.createCommandSourceStack())){
                CommandFeedback.failure(owner,Component.literal("修改面板需要管理员权限。"));return;}
            var action=actions.get(slot);if(action!=null)try{action.run();}catch(RuntimeException invalid){CommandFeedback.failure(owner,Component.literal(invalid.getMessage()==null?"面板设置失败":invalid.getMessage()));}
            broadcastChanges();
        }
        @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return p==owner&&p.level()==level
                &&(sourceHead==null?p.distanceToSqr(part.placement.point(part.width()/2.0,1.5,0))<100:
                p.distanceToSqr(sourceHead.placement.point(5.5,2,0))<100
                &&PartConnections.find(level,PartConnections.Kind.HEAD,sourceHead.placement)==sourceHead
                &&PartConnections.integrity(level,sourceHead)==PartConnections.Status.CONNECTED
                &&PartConnections.target(sourceHead.placement,part.kind).equals(part.placement))
                &&PartConnections.find(level,part.kind,part.placement)==part&&PartConnections.integrity(level,part)==PartConnections.Status.CONNECTED;}
    }
}
