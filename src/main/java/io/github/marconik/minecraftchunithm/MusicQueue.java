package io.github.marconik.minecraftchunithm;

import com.google.gson.Gson;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;

/** Shared server API for the three-song queue and future full song selector. */
public final class MusicQueue {
    private MusicQueue(){}
    public record Row(int songId,String title,String artist,String texture,String coverModel,String difficulty,String level,String constant,String status){}
    public record View(double speed,boolean mirror,boolean connected,boolean writable,boolean canSelect,List<Row> rows){
        public static View empty(){return new View(10,false,false,false,false,List.of(emptyRow(),emptyRow(),emptyRow()));}
    }
    private static Row emptyRow(){return new Row(0,"","","","","","","","EMPTY");}
    public record Snapshot(int containerId,String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,"music_queue"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (buffer,value)->{buffer.writeVarInt(value.containerId);buffer.writeUtf(value.json,16384);},
            buffer->new Snapshot(buffer.readVarInt(),buffer.readUtf(16384)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void initialize(){PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);}
    public static String encode(View view){return new Gson().toJson(view);}
    public static View decode(String json){
        var result=new Gson().fromJson(json,View.class);
        if(result==null||result.rows()==null||result.rows().size()!=3||!Double.isFinite(result.speed()))throw new IllegalArgumentException("Invalid queue snapshot");
        return result;
    }
    private static PartConnections.Part panel(ServerLevel level,PartConnections.Part head){
        var song=PartConnections.find(level,PartConnections.Kind.SONG,PartConnections.target(head.placement,PartConnections.Kind.SONG));
        return song!=null&&PartConnections.integrity(level,song)==PartConnections.Status.CONNECTED?song:null;
    }
    private static SongLibrary.Chart lookup(ServerLevel level,SongDisplayData input){
        return SongLibrary.charts(level).stream().filter(c->c.difficulty().equals(input.difficulty().name())
            &&(c.coverModel().equals(input.coverModel().toString())||c.title().equals(input.title()))).findFirst().orElse(null);
    }
    private static MusicQueueData resolve(ServerLevel level,SongQueueData inputs,MusicQueueData old,double speed,boolean mirror){
        return inputs.resolve(old,input->{var chart=lookup(level,input);return chart==null?MusicQueueData.Choice.empty():new MusicQueueData.Choice(chart.songId(),chart.difficulty(),false);},speed,mirror);
    }
    /** The head only caches the connected panel's state; it never supplies fallback songs. */
    private static PartConnections.Part source(ServerLevel level,PartConnections.Part head){
        var panel=panel(level,head);
        if(panel==null){head.music=MusicQueueData.empty();ChartPlayback.unload(level,head.placement);return null;}
        var resolved=resolve(level,panel.songInputs,panel.music,panel.music.speed(),panel.music.mirror());
        if(!resolved.equals(panel.music)){panel.music=resolved;PartConnections.save(level);}
        head.music=panel.music;
        boolean selected=false;
        for(var choice:head.music.slots())if(choice.songId()>0&&!ChartPlayback.queueStatus(level,head.placement,choice.songId(),choice.difficulty()).isEmpty()){selected=true;break;}
        if(!selected)ChartPlayback.unload(level,head.placement);
        return panel;
    }
    static void refreshSource(ServerLevel level,PartConnections.Part head){source(level,head);}
    static boolean selectionLocked(ServerLevel level,PartConnections.Part panel){
        return PartConnections.ofKind(level,PartConnections.Kind.HEAD).stream()
            .filter(head->PartConnections.target(head.placement,PartConnections.Kind.SONG).equals(panel.placement))
            .anyMatch(head->HeadControlServices.transport(level,head.placement).state()==HeadControlServices.Playback.PLAYING);
    }
    public static View panelSnapshot(ServerLevel level,PartConnections.Part panel,boolean permission){
        var queue=resolve(level,panel.songInputs,panel.music,panel.music.speed(),panel.music.mirror());
        if(!queue.equals(panel.music)){panel.music=queue;PartConnections.save(level);}
        boolean intact=PartConnections.integrity(level,panel)==PartConnections.Status.CONNECTED;
        return new View(queue.speed(),queue.mirror(),intact,permission&&intact,permission&&intact&&!selectionLocked(level,panel),
            rows(panel.songInputs,queue,c->SongLibrary.find(level,c),c->{
                for(var head:PartConnections.ofKind(level,PartConnections.Kind.HEAD))if(PartConnections.target(head.placement,PartConnections.Kind.SONG).equals(panel.placement)){
                    String status=ChartPlayback.queueStatus(level,head.placement,c.songId(),c.difficulty());if(!status.isEmpty())return status;
                }
                return "";
            }));
    }
    public static View snapshot(ServerLevel level,PartConnections.Part head,boolean permission){
        var panel=source(level,head);var queue=head.music;boolean linked=panel!=null;
        boolean playing=HeadControlServices.transport(level,head.placement).state()==HeadControlServices.Playback.PLAYING;
        var inputs=panel==null?SongQueueData.empty():panel.songInputs;
        return new View(queue.speed(),queue.mirror(),linked,permission&&linked,permission&&linked&&!playing&&!SongLibrary.charts(level).isEmpty(),
            rows(inputs,queue,choice->SongLibrary.find(level,choice),choice->ChartPlayback.queueStatus(level,head.placement,choice.songId(),choice.difficulty())));
    }
    static List<Row> rows(SongQueueData inputs,MusicQueueData queue,java.util.function.Function<MusicQueueData.Choice,SongLibrary.Chart> charts,
            java.util.function.Function<MusicQueueData.Choice,String> playback){
        List<Row> rows=new ArrayList<>();
        boolean selectedUsed=false;
        for(int i=0;i<3;i++){
            var choice=queue.slots().get(i);var input=inputs.slots().get(i);
            if(input==null){rows.add(emptyRow());continue;}
            var chart=charts.apply(choice);
            String status=choice.played()?"PLAYED":chart==null?"MISSING":"READY";
            String selected=choice.songId()>0?playback.apply(choice):"";
            if(!selected.isEmpty()&&!choice.played()&&!selectedUsed){status=selected;selectedUsed=true;}
            rows.add(new Row(choice.songId(),input.title(),input.artist(),chart!=null&&chart.coverModel().equals(input.coverModel().toString())?chart.texture():"",
                input.coverModel().toString(),input.difficulty().name(),coarseLevel(input.level()),chart==null?"":chart.constant(),status));
        }
        return List.copyOf(rows);
    }
    /** Call on the server thread; future selectors can supply an exact song and difficulty. */
    public static void setChoice(ServerLevel level,PartConnections.Part head,int slot,MusicQueueData.Choice choice){
        var panel=source(level,head);if(panel==null)throw new IllegalArgumentException("歌曲面板未连接");
        setPanelChoice(level,panel,slot,choice);
    }
    public static void setPanelChoice(ServerLevel level,PartConnections.Part panel,int slot,MusicQueueData.Choice choice){
        if(selectionLocked(level,panel))throw new IllegalArgumentException("请先暂停，再更换待播曲目");
        SongDisplayData input=null;
        if(choice.songId()!=0){
            var chart=SongLibrary.find(level,choice);if(chart==null)throw new IllegalArgumentException("该难度没有可用谱面");
            if(!Set.of("MASTER","EXPERT","ULTIMA").contains(chart.difficulty()))throw new IllegalArgumentException("歌曲面板暂不支持该难度");
            input=new SongDisplayData(Identifier.parse(chart.coverModel()),chart.title(),chart.artist(),slot+1,SongDisplayData.Difficulty.valueOf(chart.difficulty()),chart.level(),panel.music.speed(),panel.music.mirror());
        }
        setPanelInputs(level,panel,panel.songInputs.withSlot(slot,input),panel.music.speed(),panel.music.mirror(),slot);
    }
    public static void cycle(ServerLevel level,PartConnections.Part head,int slot,int direction){
        var choices=SongLibrary.charts(level);if(choices.isEmpty())return;
        var current=head.music.slots().get(slot);int index=-1;
        for(int i=0;i<choices.size();i++)if(choices.get(i).songId()==current.songId()&&choices.get(i).difficulty().equals(current.difficulty()))index=i;
        int next=index<0?(direction>0?0:choices.size()-1):Math.floorMod(index+direction,choices.size());var chart=choices.get(next);
        setChoice(level,head,slot,new MusicQueueData.Choice(chart.songId(),chart.difficulty(),false));
    }
    public static void settings(ServerLevel level,PartConnections.Part head,MusicQueueData data){
        var panel=source(level,head);if(panel==null)throw new IllegalArgumentException("歌曲面板未连接");
        ChartPlayback.configure(level,head.placement,data.speed(),data.mirror());
        panel.music=new MusicQueueData(data.speed(),data.mirror(),panel.music.slots());head.music=panel.music;
        updatePanel(level,panel);
    }
    /** Input interface for the song panel, shared by its provider, JSON command and future selector. */
    public static void setPanelInputs(ServerLevel level,PartConnections.Part panel,SongQueueData inputs,double speed,boolean mirror){
        setPanelInputs(level,panel,inputs,speed,mirror,-1);
    }
    private static void setPanelInputs(ServerLevel level,PartConnections.Part panel,SongQueueData inputs,double speed,boolean mirror,int resetSlot){
        if(panel.kind!=PartConnections.Kind.SONG||PartConnections.find(level,panel.kind,panel.placement)!=panel
                ||PartConnections.integrity(level,panel)!=PartConnections.Status.CONNECTED)throw new IllegalArgumentException("歌曲面板未连接或结构不完整");
        var next=resolve(level,inputs,panel.music,speed,mirror);
        if(resetSlot>=0){var choice=next.slots().get(resetSlot);next=next.withChoice(resetSlot,new MusicQueueData.Choice(choice.songId(),choice.difficulty(),false));}
        var heads=PartConnections.ofKind(level,PartConnections.Kind.HEAD).stream()
            .filter(h->PartConnections.target(h.placement,PartConnections.Kind.SONG).equals(panel.placement)).toList();
        for(var head:heads){
            if(!panel.songInputs.sameSongs(inputs)&&HeadControlServices.transport(level,head.placement).state()==HeadControlServices.Playback.PLAYING)
                throw new IllegalArgumentException("请先暂停，再更换待播曲目");
            ChartPlayback.configure(level,head.placement,speed,mirror);
        }
        panel.songInputs=inputs;panel.music=next;PartConnections.save(level);
        for(var head:heads){
            head.music=next;boolean keep=keepPrepared(next,choice->ChartPlayback.queueStatus(level,head.placement,choice.songId(),choice.difficulty()));
            if(!keep){ChartPlayback.unload(level,head.placement);if(!ChartPlayback.busyElsewhere(level,head.placement))prepare(level,head);}
        }
        updatePanel(level,panel);
    }
    /** Before starting, use TRACK order even if slots were configured out of order. */
    static boolean keepPrepared(MusicQueueData queue,java.util.function.Function<MusicQueueData.Choice,String> status){
        MusicQueueData.Choice first=null;
        for(var choice:queue.slots())if(choice.songId()>0&&!choice.played()){
            if(first==null)first=choice;
            String current=status.apply(choice);if(current.equals("PAUSED")||current.equals("PLAYING"))return true;
        }
        return first!=null&&status.apply(first).equals("READY");
    }
    static String coarseLevel(String value){
        try{double number=Double.parseDouble(value);return (int)number+(number-Math.floor(number)>=.5?"+":"");}
        catch(NumberFormatException text){return value;}
    }
    public static void prepare(ServerLevel level,PartConnections.Part head){
        var panel=source(level,head);if(panel==null)return;
        if(ChartPlayback.busyElsewhere(level,head.placement))throw new IllegalArgumentException("同一维度的另一台机台正在播放或暂停中");
        var track=PartConnections.find(level,PartConnections.Kind.TRACK,PartConnections.target(head.placement,PartConnections.Kind.TRACK));
        if(track==null||PartConnections.integrity(level,track)!=PartConnections.Status.CONNECTED)return;
        for(var choice:head.music.slots())if(choice.songId()!=0&&!choice.played()){
            var chart=SongLibrary.find(level,choice);if(chart==null)continue;
            try{ChartPlayback.loadSong(level,track,chart,head.music.speed(),head.music.mirror());updatePanel(level,panel);}
            catch(java.io.IOException error){throw new IllegalArgumentException("读取谱面失败",error);}return;
        }
    }
    public static void finished(ServerLevel level,PartConnections.Part track,int songId,String difficulty){
        for(var head:PartConnections.ofKind(level,PartConnections.Kind.HEAD))if(PartConnections.target(head.placement,PartConnections.Kind.TRACK).equals(track.placement)){
            var panel=source(level,head);if(panel==null)continue;var queue=panel.music;
            for(int i=0;i<3;i++){var choice=queue.slots().get(i);if(!choice.played()&&choice.songId()==songId&&choice.difficulty().equals(difficulty)){
                panel.music=queue.withChoice(i,new MusicQueueData.Choice(songId,difficulty,true));head.music=panel.music;updatePanel(level,panel);break;
            }}
        }
    }
    private static void updatePanel(ServerLevel level,PartConnections.Part panel){
        int active=-1;
        for(var head:PartConnections.ofKind(level,PartConnections.Kind.HEAD))if(PartConnections.target(head.placement,PartConnections.Kind.SONG).equals(panel.placement))
            for(int i=0;i<3;i++){
                var choice=panel.music.slots().get(i);if(choice.songId()==0||choice.played())continue;
                String status=ChartPlayback.queueStatus(level,head.placement,choice.songId(),choice.difficulty());
                if(!status.isEmpty()&&!status.equals("PLAYED")){active=i;break;}
            }
        SongPanel.display(level,panel.placement,displayData(panel.songInputs,panel.music,active));
    }
    static SongDisplayData displayData(SongQueueData inputs,MusicQueueData queue){return displayData(inputs,queue,-1);}
    static SongDisplayData displayData(SongQueueData inputs,MusicQueueData queue,int active){
        SongDisplayData selected=active>=0&&active<3?inputs.slots().get(active):null;
        if(selected==null)for(int i=0;i<3;i++)if(inputs.slots().get(i)!=null&&!queue.slots().get(i).played()){selected=inputs.slots().get(i);break;}
        if(selected==null)for(var input:inputs.slots())if(input!=null){selected=input;break;}
        if(selected==null)selected=new SongDisplayData(SongDisplayData.defaults().coverModel(),"","",1,SongDisplayData.Difficulty.MASTER,"",queue.speed(),queue.mirror());
        var s=selected;return new SongDisplayData(s.coverModel(),s.title(),s.artist(),s.track(),s.difficulty(),s.level(),queue.speed(),queue.mirror());
    }
}
