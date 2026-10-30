package io.github.marconik.minecraftchunithm;

/** Client mixer multipliers; 1 preserves the existing music/cue balance. */
public record AudioVolume(double system,double effects) {
    public static final AudioVolume DEFAULT=new AudioVolume(1,1);
    public AudioVolume {system=bounded(system);effects=bounded(effects);}
    private static double bounded(double value){return Double.isFinite(value)?Math.clamp(value,0,2):1;}
    public double gain(boolean effect){return system*(effect?effects:1);}
    /** The native sound gain handles attenuation; PCM provides gain above unity,
     * since Minecraft clamps sound-instance volume to 1 before sending it to OpenAL. */
    public static short amplify(short sample,double gain){
        return (short)Math.clamp(Math.round(sample*Math.max(1,gain)),Short.MIN_VALUE,Short.MAX_VALUE);
    }
}
