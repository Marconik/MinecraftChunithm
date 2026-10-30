package io.github.marconik.minecraftchunithm;

/** Mixer range and PCM safety checks, including the original default samples. */
public final class AudioVolumeCheck {
    public static void main(String[] args){run();}
    static void run(){
        if(!AudioVolume.DEFAULT.equals(new AudioVolume(1,1)))throw new AssertionError("Default must be centred at unity");
        var bounded=new AudioVolume(-1,3);
        if(bounded.system()!=0||bounded.effects()!=2)throw new AssertionError("Fader range must be 0..2");
        if(!new AudioVolume(Double.NaN,Double.POSITIVE_INFINITY).equals(AudioVolume.DEFAULT))throw new AssertionError("Invalid config value");
        for(int i=Short.MIN_VALUE;i<=Short.MAX_VALUE;i++){
            short sample=(short)i;
            if(AudioVolume.amplify(sample,1)!=sample)throw new AssertionError("Default PCM changed");
            for(double gain:new double[]{0,.5,1,1.5,2,4}){
                short actual=AudioVolume.amplify(sample,gain);
                long expected=Math.clamp(Math.round(i*Math.max(1,gain)),Short.MIN_VALUE,Short.MAX_VALUE);
                if(actual!=expected)throw new AssertionError("PCM overflow/wrap");
            }
        }
        for(int i=0;i<=200;i++)for(int j=0;j<=200;j++){
            var volume=new AudioVolume(i/100.0,j/100.0);
            if(Math.abs(volume.gain(false)-i/100.0)>1e-12||Math.abs(volume.gain(true)-i*j/10000.0)>1e-12)
                throw new AssertionError("System/effect gain coupling");
        }
        System.out.println("Audio mixer: centred defaults, linear 0..2 faders, master/effect coupling and all 16-bit PCM values passed.");
    }
}
