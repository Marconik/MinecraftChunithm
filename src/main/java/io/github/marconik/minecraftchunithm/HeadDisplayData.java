package io.github.marconik.minecraftchunithm;

/** Input-only view state. Completion is derived from the score, capped visually at 100%. */
public record HeadDisplayData(long critical,long justice,long attack,long miss,long maxCombo,long score) {
    public static final long MAX_SCORE=1_010_000;
    public HeadDisplayData {
        if(critical<0||justice<0||attack<0||miss<0||maxCombo<0||score<0)throw new IllegalArgumentException("Invalid head display input");
    }
    public double percent(){ return Math.min(score,MAX_SCORE)*100.0/MAX_SCORE; }
    public int filledCells(){ return (int)(Math.min(score,MAX_SCORE)/101_000); }
    public double barFraction(){ return Math.min(score,MAX_SCORE)%101_000/101_000.0; }
    public boolean goldBar(){ return filledCells()>=3; }
}
