package io.github.marconik.minecraftchunithm;

/** Pixel viewport motion: pause, travel, pause, return. No movement for fitting text. */
public final class PanelMarquee {
    private PanelMarquee(){}
    public static double offset(double seconds,double overflow){
        if(overflow<=0)return 0;
        double pause=1.5,travel=overflow/80.0;
        double phase=Math.max(0,seconds)%(2*(pause+travel));
        if(phase<pause)return 0;
        if(phase<pause+travel)return (phase-pause)*80;
        if(phase<2*pause+travel)return overflow;
        return Math.max(0,overflow-(phase-2*pause-travel)*80);
    }
}
