package io.github.marconik.minecraftchunithm;

import java.util.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AutoScoreCheck {
    private static ChartDefinition.Note note(String id,String type,double start,double end,boolean root,boolean terminal){
        return new ChartDefinition.Note(id,type,start,end,4,2,4,2,"",root,terminal,0,0,type.equals("ALD")?"NON":"DEF");
    }
    private static ChartDefinition chart(List<ChartDefinition.Note> notes){
        return new ChartDefinition("Automatic score fixture",notes,List.of(new ChartDefinition.Speed(50,950,-1)),List.of(10.0),List.of(20.0),2000);
    }
    public static void run(){
        var basic=AutoScoreTimeline.of(chart(List.of(note("a","TAP",100,100,false,false),note("b","CHR",200,200,false,false),note("c","FLK",200,200,false,false))));
        if(basic.at(99).passed()!=0||basic.at(100).score()!=336666||basic.at(200).passed()!=3||basic.at(200).score()!=1010000)
            throw new AssertionError("Floor division, judgment time, chords or final maximum failed");
        if(basic.at(150).passed()!=1||basic.at(150).score()!=336666||basic.at(0).score()!=0)
            throw new AssertionError("Seeking must recompute score without duplicate events");
        var notes=new ArrayList<ChartDefinition.Note>();
        notes.add(note("hold","HLD",100,200,true,true));notes.add(note("exhold","HXD",100,200,true,true));
        notes.add(note("slide-head","SLC",300,400,true,false));
        notes.add(note("control","SXC",400,500,false,false));
        notes.add(note("slide-node","SLD",500,600,false,false));
        notes.add(note("slide-tail","SLC",600,700,false,true));
        notes.add(note("air","AIR",300,300,false,false));notes.add(note("down","ADW",300,300,false,false));
        notes.add(note("ahd1","AHD",800,900,true,false));notes.add(note("ahd2","AHD",900,1000,false,true));
        notes.add(note("asc","ASC",1100,1200,true,false));notes.add(note("asc-control","ASC",1200,1300,false,false));
        notes.add(note("asd","ASD",1300,1400,false,true));
        notes.add(note("mine","MNE",1500,1500,false,false));notes.add(note("effect","ALD",1500,2000,false,false));
        var timeline=AutoScoreTimeline.of(chart(notes));
        // Hold 4 + Slide 3 + Air 2 + chained AHD 3 + ASC/ASD 3 + Mine 1.
        if(timeline.at(2000).total()!=16||timeline.at(2000).passed()!=16||timeline.at(2000).score()!=1010000)
            throw new AssertionError("Visible sustain/action nodes were lost, duplicated or decoration scored");
        var mines=AutoScoreTimeline.of(chart(List.of(note("mine","MNE",100,100,false,false),note("tap","TAP",100,100,false,false))));
        if(mines.at(99).passed()!=0||mines.at(100).passed()!=2||mines.at(100).display().critical()!=2||mines.at(100).display().maxCombo()!=2||mines.at(100).score()!=1010000)
            throw new AssertionError("Simultaneous Mine and Tap must each count at the judgment time");
        if(!NoteDefinition.preview(NoteDefinition.Kind.MINE,2).judged())throw new AssertionError("Mine definition must enable scoring");
        var empty=AutoScoreTimeline.of(chart(List.of(note("decor-only","ALD",0,100,false,false))));
        if(empty.at(2000).total()!=0||empty.at(2000).score()!=0)throw new AssertionError("An unscored chart must stay zero");
        for(int count=1;count<=1000;count++){
            var many=new ArrayList<ChartDefinition.Note>();
            for(int i=0;i<count;i++)many.add(note("n"+i,"TAP",i,i,false,false));
            var schedule=AutoScoreTimeline.of(chart(many));
            for(int i=0;i<count;i++){
                var score=schedule.at(i);long expected=(i+1L)*1010000/count;
                if(score.score()!=expected||score.display().critical()!=i+1||score.display().maxCombo()!=i+1
                        ||score.display().justice()!=0||score.display().attack()!=0||score.display().miss()!=0)
                    throw new AssertionError("Automatic critical/combo formula diverged");
                var display=score.display();
                if(display.filledCells()!=expected/101000||display.barFraction()!=(expected==1010000?1:expected%101000/101000.0))
                    throw new AssertionError("Score and progress display diverged");
            }
        }
        System.out.println("Automatic critical score: 500,500 ratios, flooring, chords, visible sustain nodes, mine inclusion/effect exclusion, seek/reset and full-score progress passed.");
        int imported=0;
        for(var entry:resource("index.json").getAsJsonArray("songs")){
            var song=entry.getAsJsonObject();
            for(var difficulty:song.getAsJsonArray("difficulties")){
                String path=String.format(Locale.ROOT,"%04d/%s.json",song.get("id").getAsInt(),difficulty.getAsString().toLowerCase(Locale.ROOT));
                var chart=ChartDefinition.fromJson(resource(path));var schedule=AutoScoreTimeline.of(chart);
                var end=schedule.at(chart.lastTimeMs());
                if(end.total()<=0||end.passed()!=end.total()||end.score()!=1010000||schedule.at(-1000).score()!=0)
                    throw new AssertionError("Imported chart scoring failed: "+path);
                long mineCount=chart.notes().stream().filter(n->n.type().equals("MNE")).count();
                if(mineCount>0){
                    var withoutMines=new ChartDefinition(chart.title(),chart.notes().stream().filter(n->!n.type().equals("MNE")).toList(),chart.speeds(),chart.markers(),chart.beats(),chart.lastTimeMs(),chart.profiles(),chart.extendedLanes());
                    if(end.total()-AutoScoreTimeline.of(withoutMines).at(chart.lastTimeMs()).total()!=mineCount)throw new AssertionError("Imported mines missing from denominator");
                    System.out.println(path+": "+mineCount+" mines included in score total "+end.total());
                }
                imported++;
            }
        }
        System.out.println("Imported chart auto-score: "+imported+" charts start at zero and finish at exactly 1,010,000.");
    }
    private static JsonObject resource(String name){
        try(var stream=AutoScoreCheck.class.getResourceAsStream("/data/minecraftchunithm/songs/"+name)){
            if(stream==null)throw new AssertionError("Missing score fixture: "+name);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException error){throw new AssertionError(error);}
    }
    public static void main(String[] args){run();}
}
