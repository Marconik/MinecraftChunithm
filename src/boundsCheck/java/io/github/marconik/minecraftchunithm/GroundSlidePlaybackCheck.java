package io.github.marconik.minecraftchunithm;

import java.util.List;

final class GroundSlidePlaybackCheck {
    private static ChartDefinition.Note note(String type,double time,double end,double lane,double width,double endLane,double endWidth){
        return new ChartDefinition.Note(type,type,time,end,lane,width,endLane,endWidth,"",true,true,0,0,"DEF");
    }
    private static ChartDefinition chart(ChartDefinition.Note... notes){
        return new ChartDefinition("Lane lights",List.of(notes),List.of(new ChartDefinition.Speed(0,2500,-1)),List.of(400.0),List.of(300.0),3000);
    }
    private static void expect(ChartDefinition chart,double time,boolean mirror,int mask){
        int actual=GroundSlidePlayback.mask(chart,time,mirror);
        if(actual!=mask)throw new AssertionError("Lane lights at "+time+" mirrored="+mirror+": "+actual+" != "+mask);
    }
    static void run(){
        for(int left=0;left<16;left++)for(int width=1;width<=16-left;width++){
            var tap=chart(note("TAP",500,500,left,width,left,width));
            int mask=((1<<width)-1)<<left,flipped=((1<<width)-1)<<(16-left-width);
            expect(tap,499.99,false,0);expect(tap,500,false,mask);expect(tap,550,false,mask);expect(tap,600,false,0);
            expect(tap,500,true,flipped);
        }
        for(String type:List.of("CHR","FLK","AIR","AUL","AUR","ADW","ADL","ADR")){
            var chart=chart(note(type,500,500,3,2,3,2));
            expect(chart,500,false,0x18);expect(chart,700,false,0);
        }
        for(String type:List.of("MNE","AHD","AHX","ASC","ASD","ALD")){
            var excluded=chart(note(type,500,type.equals("MNE")?500:1500,0,16,0,16));
            for(boolean mirror:List.of(false,true))for(double time:new double[]{499,500,550,1000,1500,1550,1600})
                expect(excluded,time,mirror,0);
        }
        for(String type:List.of("HLD","HXD","SLD","SLC","SXD","SXC")){
            var chart=chart(note(type,500,1500,0,2,8,4));
            expect(chart,1000,false,0x70);expect(chart,1000,true,0xe00);
            expect(chart,1500,false,0xf00);expect(chart,1600,false,0);
        }
        var chord=chart(note("TAP",500,500,0,1,0,1),note("MNE",500,500,15,1,15,1));
        expect(chord,500,false,1);expect(chord,500,true,0x8000);
        var ordinaryChord=chart(note("TAP",500,500,0,1,0,1),note("CHR",500,500,15,1,15,1));
        expect(ordinaryChord,500,false,0x8001);
        expect(chart(note("ALD",500,1500,0,16,0,16)),500,false,0);
        if(GroundSlidePlayback.lanes(3,1)!=8||GroundSlidePlayback.lanes(3.5,1)!=24)
            throw new AssertionError("Lane boundary coverage mismatch");
        System.out.println("Ground Slide playback: all lane widths, mirrors, chords/Air, flash expiry, moving/tapered sustains, mine/Air Action/decoration exclusion and coincident Tap preservation passed.");
    }
}
