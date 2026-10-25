package io.github.marconik.minecraftchunithm;

import java.util.*;

public final class MusicQueueCheck {
    private MusicQueueCheck(){}
    public static void run(){
        var queue=MusicQueueData.empty();
        if(queue.speed()!=20||MusicQueue.View.empty().speed()!=20||SongDisplayData.defaults().speed()!=20)
            throw new AssertionError("New machine, client snapshot and song panel must default to speed 20");
        if(queue.slots().size()!=3||queue.slots().stream().anyMatch(slot->slot.songId()!=0))throw new AssertionError("A new machine must have three empty slots");
        for(int i=0;i<100;i++){
            double before=queue.speed();queue=queue.changeSpeed(1);
            if(queue.speed()!=Math.min(30,before+.25))throw new AssertionError("Speed increment is not 0.25");
        }
        if(queue.speed()!=30)throw new AssertionError("Speed ceiling is not 30");
        if(MusicQueueData.decode(queue.encode()).speed()!=30)throw new AssertionError("Speed 30 did not survive persistence");
        try{new MusicQueueData(30.25,false,queue.slots());throw new AssertionError("Speed above 30 accepted");}catch(IllegalArgumentException expected){}
        for(int i=0;i<200;i++)queue=queue.changeSpeed(-1);
        if(queue.speed()!=.25)throw new AssertionError("Speed minimum is not 0.25");
        var played=new MusicQueueData.Choice(1100,"MASTER",true);
        queue=new MusicQueueData(10.25,true,queue.slots()).withChoice(0,played)
                .withChoice(1,new MusicQueueData.Choice(1100,"MASTER",false));
        if(!MusicQueueData.decode(queue.encode()).equals(queue))throw new AssertionError("Queue did not survive persistence round trip");
        if(!queue.slots().get(0).played()||queue.slots().get(1).played())throw new AssertionError("Repeated songs must retain separate states");
        var snapshot=MusicQueue.View.empty();
        if(!MusicQueue.decode(MusicQueue.encode(snapshot)).equals(snapshot))throw new AssertionError("Client snapshot JSON round trip failed");
        try{new MusicQueueData(Double.NaN,false,queue.slots());throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}
        try{new MusicQueueData(10,false,List.of(played));throw new AssertionError("Invalid slot count accepted");}catch(IllegalArgumentException expected){}
        try{queue.withChoice(3,played);throw new AssertionError("Fourth slot accepted");}catch(IllegalArgumentException expected){}
        checkPanelInputs();
        checkSongSelector();
        System.out.println("Music queue: three independent slots, 0.25 speed steps and limits, mirror and played-state persistence passed.");
    }
    private static void checkSongSelector(){
        for(int slot=0;slot<3;slot++)for(int songId:new int[]{103,180,2475,999999})for(String difficulty:List.of("BASIC","ADVANCED","EXPERT","MASTER","ULTIMA","WORLD'S END")){
            var decoded=SongPanelMenu.selection(SongPanelMenu.selectAction(slot,songId,difficulty));
            if(decoded.slot()!=slot||decoded.songId()!=songId||!decoded.difficulty().equals(difficulty))throw new AssertionError("Stable song/slot/difficulty action did not round trip");
        }
        for(int invalid:new int[]{Integer.MIN_VALUE,-1,0,1,2,32,32+180*8+6,Integer.MAX_VALUE}){
            try{SongPanelMenu.selection(invalid);throw new AssertionError("Malformed selector action accepted");}catch(IllegalArgumentException expected){}
        }
        try{SongPanelMenu.selectAction(3,180,"MASTER");throw new AssertionError("Fourth selector slot accepted");}catch(IllegalArgumentException expected){}
        var entry=new SongLibrary.Song(180,"怒槌","光吉猛修","minecraftchunithm:textures/song/0180/cover.png",
            List.of(new SongLibrary.Difficulty("EXPERT","14","14.0",false),new SongLibrary.Difficulty("MASTER","15","15.4",true)));
        var entries=List.of(entry,entry,entry);
        var blank=entry.difficulties().getLast();
        if(!blank.name().equals("ULTIMA")||!blank.constant().isEmpty()||!blank.level().isEmpty()||blank.available())
            throw new AssertionError("Missing ULTIMA must retain an empty, non-selectable label");
        var realUltima=new SongLibrary.Difficulty("ULTIMA","15+","15.8",true);
        var withUltima=new SongLibrary.Song(180,entry.title(),entry.artist(),entry.texture(),List.of(realUltima,entry.difficulties().get(1)));
        if(withUltima.difficulties().stream().filter(d->d.name().equals("ULTIMA")).count()!=1||!withUltima.difficulties().getLast().equals(realUltima))
            throw new AssertionError("An existing ULTIMA chart must keep its constant and availability, without a duplicate slot");
        var empty=MusicQueue.View.empty();var queue=new MusicQueue.View(10,false,true,true,false,empty.rows());
        var state=new SongPanelMenu.State(queue,6,9,entries);
        if(!SongPanelMenu.decode(SongPanelMenu.encode(state)).equals(state)||SongPanelMenu.decode(SongPanelMenu.encode(state)).queue().canSelect())
            throw new AssertionError("Catalog metadata, unavailable difficulties or playback lock were lost in synchronization");
        try{SongPanelMenu.decode(SongPanelMenu.encode(new SongPanelMenu.State(queue,7,9,entries)));throw new AssertionError("Overscrolled window accepted");}catch(IllegalArgumentException expected){}
        try{SongPanelMenu.decode(SongPanelMenu.encode(new SongPanelMenu.State(queue,0,9,List.of(entry))));throw new AssertionError("Incomplete three-song window accepted");}catch(IllegalArgumentException expected){}
        try{SongPanelMenu.selection(SongPanelMenu.scrollAction(0));throw new AssertionError("Scroll action overlaps song choice actions");}catch(IllegalArgumentException expected){}
        if(SongPanelMenu.clampOffset(-1,9)!=0||SongPanelMenu.clampOffset(1000,9)!=6||SongPanelMenu.clampOffset(5,2)!=0||SongPanelMenu.clampOffset(5,0)!=0)
            throw new AssertionError("Scrolling must preserve three visible songs and clamp at both ends");
        if(!SongPanelMenu.decode(SongPanelMenu.encode(SongPanelMenu.State.empty())).equals(SongPanelMenu.State.empty()))throw new AssertionError("Empty scroll state did not round trip");
        if(!SongLibrary.displayConstant("15.40").equals("15.4")||!SongLibrary.displayConstant("14").equals("14.0"))throw new AssertionError("Exact difficulty constant formatting failed");
        var pending=new MusicQueueData(10,false,List.of(new MusicQueueData.Choice(103,"MASTER",false),new MusicQueueData.Choice(180,"MASTER",false),MusicQueueData.Choice.empty()));
        if(MusicQueue.keepPrepared(pending,c->c.songId()==180?"READY":""))throw new AssertionError("Selecting TRACK 2 before TRACK 1 must not change playback order");
        if(!MusicQueue.keepPrepared(pending,c->c.songId()==103?"READY":"")||!MusicQueue.keepPrepared(pending,c->c.songId()==180?"PAUSED":""))
            throw new AssertionError("Ready first track or an active paused chart was unnecessarily replaced");
        System.out.println("Song selector: stable choices, three-song scrolling bounds, exact constants, invalid requests and lock synchronization passed.");
    }
    private static void checkPanelInputs(){
        var inputs=SongQueueData.empty();
        var song=new SongDisplayData(net.minecraft.resources.Identifier.parse("minecraftchunithm:cover/song/0180"),"面板输入曲名","输入的创作者",2,
            SongDisplayData.Difficulty.MASTER,"14.7",10,false);
        inputs=inputs.withSlot(1,song);
        if(!SongQueueData.decode(inputs.encode()).equals(inputs))throw new AssertionError("Panel inputs did not survive persistence with empty slots");
        var choice=new MusicQueueData.Choice(180,"MASTER",false);
        var queue=inputs.resolve(MusicQueueData.empty(),input->choice,10.25,true);
        var panel=MusicQueue.displayData(inputs,queue);
        if(panel.speed()!=10.25||!panel.mirror()||!panel.title().equals(song.title())||!panel.artist().equals(song.artist())||panel.track()!=2)
            throw new AssertionError("Speed/mirror projection changed the song panel input or failed to update its display");
        var chart=new SongLibrary.Chart(180,"目录中的不同曲名","不同创作者","minecraftchunithm:textures/song/0180/cover.png",song.coverModel().toString(),"MASTER","15","15.4","test");
        var rows=MusicQueue.rows(inputs,queue,c->c.songId()==180?chart:null,c->"READY");
        if(!rows.get(0).title().isEmpty()||!rows.get(0).status().equals("EMPTY")||!rows.get(2).artist().isEmpty()
                ||!rows.get(1).title().equals(song.title())||!rows.get(1).artist().equals(song.artist())||!rows.get(1).level().equals("14+"))
            throw new AssertionError("Queue must use panel inputs, leave missing inputs empty, and show coarse levels");
        var played=queue.withChoice(1,new MusicQueueData.Choice(180,"MASTER",true));
        if(!inputs.resolve(played,input->choice,11,false).slots().get(1).played())throw new AssertionError("Settings reset the played marker");
        if(inputs.resolve(played,input->new MusicQueueData.Choice(103,"MASTER",false),11,false).slots().get(1).played())throw new AssertionError("Changing chart retained an old played marker");
        var changedSettings=inputs.withSlot(1,new SongDisplayData(song.coverModel(),song.title(),song.artist(),2,song.difficulty(),song.level(),11,true));
        if(!inputs.sameSongs(changedSettings))throw new AssertionError("Settings-only changes must be allowed during playback");
        var unknown=inputs.resolve(MusicQueueData.empty(),input->MusicQueueData.Choice.empty(),10,false);
        if(!MusicQueue.rows(inputs,unknown,c->null,c->"").get(1).title().equals(song.title()))throw new AssertionError("Unimported chart lost its panel metadata");
        var cleared=SongQueueData.empty();var clearedQueue=cleared.resolve(played,input->choice,10.5,true);
        if(MusicQueue.rows(cleared,clearedQueue,c->chart,c->"READY").stream().anyMatch(r->!r.title().isEmpty()||!r.status().equals("EMPTY"))
                ||!MusicQueue.displayData(cleared,clearedQueue).title().isEmpty()||MusicQueue.displayData(cleared,clearedQueue).speed()!=10.5)
            throw new AssertionError("Cleared panel must not restore old head selections, and must still display speed");
        if(!MusicQueue.coarseLevel("14.4").equals("14")||!MusicQueue.coarseLevel("14+").equals("14+")||!MusicQueue.coarseLevel("15").equals("15"))
            throw new AssertionError("Coarse level conversion failed");
        try{inputs.withSlot(0,song);throw new AssertionError("Mismatched TRACK accepted");}catch(IllegalArgumentException expected){}
        System.out.println("Song panel input source, sparse/cleared slots, metadata, display settings, persistence and played-state isolation passed.");
    }
}
