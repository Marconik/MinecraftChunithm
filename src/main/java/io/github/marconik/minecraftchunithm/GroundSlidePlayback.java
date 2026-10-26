package io.github.marconik.minecraftchunithm;

/** Automatic lane lights in musical time, before input judging is available. */
final class GroundSlidePlayback {
    static final double FLASH_MS=100;
    private GroundSlidePlayback(){}

    static int mask(ChartDefinition chart,double time,boolean mirror){
        int mask=0;
        for(var note:chart.notes()){
            boolean start=false,end=false,sustain=false;
            switch(note.type()){
                case "TAP","CHR","FLK","AIR","AUL","AUR","ADW","ADL","ADR"->start=true;
                case "HLD","HXD"->{start=true;end=true;sustain=true;}
                case "SLD","SLC","SXD","SXC"->{
                    start=note.root()||note.type().endsWith("D");end=note.terminal();sustain=true;
                }
                // Mines and airborne action paths do not press the ground slider.
                case "MNE","AHD","AHX","ASC","ASD","ALD"->{}
                default->throw new IllegalArgumentException("Unsupported lane light note: "+note.type());
            }
            if(start&&time>=note.time()&&time<note.time()+FLASH_MS)
                mask|=lanes(note.laneAt(note.time(),mirror),note.width());
            if(end&&time>=note.end()&&time<note.end()+FLASH_MS)
                mask|=lanes(note.laneAt(note.end(),mirror),note.endWidth());
            if(sustain&&time>=note.time()&&time<note.end())
                mask|=lanes(note.laneAt(time,mirror),note.widthAt(time));
        }
        return mask;
    }

    /** Cover every intersected half-block cell without spilling onto a touching neighbour. */
    static int lanes(double left,double width){
        int mask=0,first=Math.max(0,(int)Math.floor(left+1e-8)),last=Math.min(16,(int)Math.ceil(left+width-1e-8));
        for(int lane=first;lane<last;lane++)mask|=1<<lane;
        return mask;
    }
}
