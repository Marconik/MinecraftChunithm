package io.github.marconik.minecraftchunithm;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;

/** Server-thread extension points. Default devices and playback are deliberately unavailable. */
public final class HeadControlServices {
    private HeadControlServices(){}
    public enum Playback { STOPPED, PLAYING, PAUSED, FINISHED }
    public enum Action { START_OR_RESUME, PAUSE, STOP }
    public record Input(boolean leftAirConnected,boolean rightAirConnected,boolean groundConnected,
                        double leftAir,double rightAir,double[] ground) {
        public Input {
            if(ground==null||ground.length!=16)throw new IllegalArgumentException("Exactly 16 ground responses required");
            ground=ground.clone();leftAir=clamp(leftAir);rightAir=clamp(rightAir);
            for(int i=0;i<16;i++)ground[i]=clamp(ground[i]);
        }
        @Override public double[] ground(){return ground.clone();}
        private static double clamp(double n){return Double.isFinite(n)?Math.clamp(n,0,1):0;}
        public static Input offline(){return new Input(false,false,false,0,0,new double[16]);}
    }
    public record Transport(boolean ready,Playback state) {
        public Transport {Objects.requireNonNull(state);}
        public static Transport offline(){return new Transport(false,Playback.STOPPED);}
    }
    public interface InputProvider {Input snapshot(ServerLevel level,Placement head);}
    public interface PlaybackBackend {
        /** ready means a selected playable chart is loaded, not just a connected song panel. */
        Transport snapshot(ServerLevel level,Placement head);
        void execute(ServerLevel level,Placement head,Action action);
    }
    private static Input physicalInput(ServerLevel level,Placement head){
        var track=PartConnections.find(level,PartConnections.Kind.TRACK,PartConnections.target(head,PartConnections.Kind.TRACK));
        boolean connected=false;
        if(track!=null&&PartConnections.integrity(level,track)==PartConnections.Status.CONNECTED){
            var ground=PartConnections.find(level,PartConnections.Kind.GROUND_SLIDE,PartConnections.groundTarget(track.placement,track.length));
            connected=ground!=null&&PartConnections.integrity(level,ground)==PartConnections.Status.CONNECTED;
        }
        return new Input(false,false,connected,0,0,new double[16]);
    }
    private static InputProvider inputs=HeadControlServices::physicalInput;
    private static PlaybackBackend playback;
    public static void setInputProvider(InputProvider provider){inputs=Objects.requireNonNull(provider);}
    public static void setPlaybackBackend(PlaybackBackend backend){playback=Objects.requireNonNull(backend);}
    public static void reset(){inputs=HeadControlServices::physicalInput;playback=null;}
    static Input input(ServerLevel level,Placement head){return Objects.requireNonNull(inputs.snapshot(level,head));}
    static Transport transport(ServerLevel level,Placement head){return playback==null?Transport.offline():Objects.requireNonNull(playback.snapshot(level,head));}
    static void execute(ServerLevel level,Placement head,Action action){if(playback!=null)playback.execute(level,head,action);}
    public static boolean allowed(Action action,Transport state){
        return switch(action){
            case START_OR_RESUME->state.ready()&&state.state()!=Playback.PLAYING;
            // Idempotent safety controls remain available without a chart.
            case PAUSE,STOP->true;
        };
    }
}
