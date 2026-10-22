package io.github.marconik.minecraftchunithm;

import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Vanilla inventory-backed screens; button items cannot be removed or inserted. */
final class PartWindows {
    static void initialize(){
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(!(world instanceof ServerLevel level)||!(player instanceof ServerPlayer sp))return InteractionResult.PASS;
            var part=PartConnections.at(level,hit.getBlockPos());
            if(part==null||hit.getDirection()!=part.placement.forward().getOpposite())return InteractionResult.PASS;
            // Restrict activation to the blocks behind the visible terminal.
            boolean terminal=false;
            for(int y=1;y<3;y++)
                for(int x=part.kind==PartConnections.Kind.HEAD?5:3;x<=(part.kind==PartConnections.Kind.HEAD?5:3);x++)
                    if(part.placement.block(x,y,0).equals(hit.getBlockPos()))terminal=true;
            if(!terminal)return InteractionResult.PASS;
            if(PartConnections.integrity(level,part)!=PartConnections.Status.CONNECTED){sp.sendSystemMessage(Component.literal("部件损坏或区块未加载，请恢复完整结构。"));return InteractionResult.SUCCESS;}
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
            if(part.kind==PartConnections.Kind.PLAYER){
                button(0,Items.NAME_TAG,"当前玩家："+part.player.name(),()->{});
                List<NameDisplayData> choices;
                try{choices=PanelServices.playerChoices(owner);}catch(RuntimeException e){MinecraftChunithm.LOGGER.error("Player info provider failed",e);choices=List.of();}
                final var list=choices;int start=page*17;
                if(list.isEmpty())button(13,Items.PAPER,"暂无可导入玩家信息（等待提供器）",()->{});
                for(int i=start;i<Math.min(start+17,list.size());i++){var value=list.get(i);button(9+i-start,Items.PLAYER_HEAD,"导入："+value.name(),()->{NamePanel.update(level,part.placement,value);reopen(page);});}
                // Navigation occupies the top row, separate from selectable profiles.
                buttons.setItem(26,ItemStack.EMPTY);actions.remove(26);
                button(8,Items.BARRIER,"关闭",owner::closeContainer);
                if(page>0)button(6,Items.ARROW,"上一页",()->reopen(page-1));
                if(start+17<list.size())button(7,Items.ARROW,"下一页",()->reopen(page+1));return;
            }
            var s=part.song;
            button(0,Items.PAPER,"当前曲目："+s.title(),()->{});
            button(2,Items.EXPERIENCE_BOTTLE,"难度："+s.difficulty()+"（点击切换）",()->set(s,new SongDisplayData(s.coverModel(),s.title(),s.artist(),s.track(),SongDisplayData.Difficulty.values()[(s.difficulty().ordinal()+1)%3],s.level(),s.speed(),s.mirror())));
            button(3,Items.FEATHER,"速度 -0.5："+s.speed(),()->speed(Math.max(.5,s.speed()-.5)));
            button(4,Items.SUGAR,"速度 +0.5："+s.speed(),()->speed(Math.min(100,s.speed()+.5)));
            button(5,Items.ENDER_PEARL,"镜像："+(s.mirror()?"ON":"OFF"),()->set(s,new SongDisplayData(s.coverModel(),s.title(),s.artist(),s.track(),s.difficulty(),s.level(),s.speed(),!s.mirror())));
            List<SongDisplayData> choices;
            try{choices=PanelServices.songChoices(owner);}catch(RuntimeException e){MinecraftChunithm.LOGGER.error("Song provider failed",e);choices=List.of();}
            final var list=choices;int start=page*9;
            if(list.isEmpty())button(13,Items.MUSIC_DISC_13,"暂无歌曲：等待歌曲目录接口接入",()->{});
            for(int i=start;i<Math.min(start+9,list.size());i++){var value=list.get(i);button(9+i-start,Items.MUSIC_DISC_13,"选择："+value.title()+" / "+value.difficulty(),()->{
                SongPanel.update(level,part.placement,new SongDisplayData(value.coverModel(),value.title(),value.artist(),value.track(),value.difficulty(),value.level(),part.song.speed(),part.song.mirror()));reopen(0);});}
            if(page>0)button(19,Items.ARROW,"上一页",()->reopen(page-1));
            if(start+9<list.size())button(20,Items.ARROW,"下一页",()->reopen(page+1));
        }
        void reopen(int page){open(owner,level,part,page,sourceHead);}
        void speed(double speed){var s=part.song;set(s,new SongDisplayData(s.coverModel(),s.title(),s.artist(),s.track(),s.difficulty(),s.level(),speed,s.mirror()));}
        void set(SongDisplayData previous,SongDisplayData value){SongPanel.update(level,part.placement,value);reopen(0);}
        void button(int slot,Item item,String label,Runnable action){var stack=new ItemStack(item);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal(label));buttons.setItem(slot,stack);actions.put(slot,action);}
        @Override public void clicked(int slot,int button,ContainerInput input,Player player){
            if(player!=owner||!stillValid(player))return;
            if(slot==26||slot==8&&part.kind==PartConnections.Kind.PLAYER){owner.closeContainer();return;}
            if(part.kind!=PartConnections.Kind.HEAD&&!net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(owner.createCommandSourceStack())){
                owner.sendSystemMessage(Component.literal("修改面板需要管理员权限。"));return;}
            var action=actions.get(slot);if(action!=null)action.run();
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
