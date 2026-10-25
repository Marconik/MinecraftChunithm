package io.github.marconik.minecraftchunithm;

import com.mojang.math.Transformation;
import com.mojang.brigadier.arguments.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.commands.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Chart and rail-audio transport. One selected track per dimension, no input judging. */
public final class ChartPlayback {
    private static final double SUSTAIN_WIDTH_SCALE=.95;
    private static final String TAG="minecraftchunithm.chart_playback";
    private static final Map<ServerLevel,Session> SESSIONS=new HashMap<>();
    private static final Set<UUID> LIVE=new HashSet<>();
    private static long nextAudioSession;
    private ChartPlayback(){}
    public static void initialize(){
        HeadControlServices.setPlaybackBackend(new HeadControlServices.PlaybackBackend(){
            private Session session(ServerLevel level,Placement head){
                var s=SESSIONS.get(level);return s!=null&&s.track.placement.equals(PartConnections.target(head,PartConnections.Kind.TRACK))?s:null;
            }
            public HeadControlServices.Transport snapshot(ServerLevel level,Placement head){
                var s=session(level,head);return s==null?HeadControlServices.Transport.offline():new HeadControlServices.Transport(true,s.state);
            }
            public void execute(ServerLevel level,Placement head,HeadControlServices.Action action){
                var s=session(level,head);if(s!=null)s.action(level,action);
            }
        });
        ServerTickEvents.START_LEVEL_TICK.register(level->{
            var s=SESSIONS.get(level);if(s==null)return;
            try{
                if(PartConnections.find(level,PartConnections.Kind.TRACK,s.track.placement)!=s.track){unload(level);return;}
                if(level.getGameTime()%20==0&&PartConnections.integrity(level,s.track)!=PartConnections.Status.CONNECTED){unload(level);return;}
                s.tick(level);
                if(level.getGameTime()%5==0)s.syncAudio(level);
            }catch(RuntimeException error){MinecraftChunithm.LOGGER.error("Chart playback stopped",error);unload(level);}
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{
            if(entity.entityTags().contains(TAG)&&!LIVE.contains(entity.getUUID()))entity.discard();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var level:new ArrayList<>(SESSIONS.keySet()))unload(level);});
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
            var chart=Commands.literal("chart").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
            for(String verb:List.of("load","play")){
                var command=Commands.literal(verb);
                if(verb.equals("play"))command.executes(c->control(c.getSource(),HeadControlServices.Action.START_OR_RESUME));
                command.then(Commands.argument("resource",StringArgumentType.string())
                    .executes(c->select(c.getSource(),StringArgumentType.getString(c,"resource"),MusicQueueData.DEFAULT_SPEED,false,verb.equals("play")))
                    .then(Commands.argument("speed",DoubleArgumentType.doubleArg(MusicQueueData.MIN_SPEED,MusicQueueData.MAX_SPEED))
                        .executes(c->select(c.getSource(),StringArgumentType.getString(c,"resource"),DoubleArgumentType.getDouble(c,"speed"),false,verb.equals("play")))
                        .then(Commands.argument("mirror",BoolArgumentType.bool()).executes(c->select(c.getSource(),StringArgumentType.getString(c,"resource"),DoubleArgumentType.getDouble(c,"speed"),BoolArgumentType.getBool(c,"mirror"),verb.equals("play"))))));
                chart.then(command);
            }
            chart.then(Commands.literal("pause").executes(c->control(c.getSource(),HeadControlServices.Action.PAUSE)))
                .then(Commands.literal("resume").executes(c->control(c.getSource(),HeadControlServices.Action.START_OR_RESUME)))
                .then(Commands.literal("stop").executes(c->control(c.getSource(),HeadControlServices.Action.STOP)))
                .then(Commands.literal("seek").then(Commands.argument("seconds",DoubleArgumentType.doubleArg(0)).executes(c->{
                    var s=SESSIONS.get(c.getSource().getLevel());if(s==null)return missing(c.getSource());
                    s.time=Math.min(s.chart.lastTimeMs()+1000,DoubleArgumentType.getDouble(c,"seconds")*1000);
                    if(s.state!=HeadControlServices.Playback.PLAYING)s.state=HeadControlServices.Playback.PAUSED;
                    s.audioRevision++;s.clear();s.render(c.getSource().getLevel());s.publishScore(c.getSource().getLevel());s.syncAudio(c.getSource().getLevel());return 1;
                })))
                .then(Commands.literal("status").executes(c->{var s=SESSIONS.get(c.getSource().getLevel());if(s==null)return missing(c.getSource());
                    c.getSource().sendSuccess(()->Component.literal(s.chart.title()+" · "+s.state+" · "+String.format(Locale.ROOT,"%.3fs",s.time/1000)
                        +" · "+s.speed+" 格/秒 · 镜像 "+s.mirror+" · 显示实体 "+s.entities.size()),false);return 1;}));
            dispatcher.register(Commands.literal("chunithm").then(chart));
        });
    }
    private static int missing(CommandSourceStack source){source.sendFailure(Component.literal("尚未加载谱面：/chunithm chart load 180 10 false"));return 0;}
    public static void unload(ServerLevel level){var s=SESSIONS.remove(level);if(s!=null){s.clear();RailAudio.clear(level);NoteCueAudio.clear(level);}}
    public static void unload(ServerLevel level,Placement head){
        var session=SESSIONS.get(level);if(session!=null&&session.track.placement.equals(PartConnections.target(head,PartConnections.Kind.TRACK)))unload(level);
    }
    public static String queueStatus(ServerLevel level,Placement head,int songId,String difficulty){
        var s=SESSIONS.get(level);
        if(s==null||s.songId!=songId||!s.difficulty.equals(difficulty)||!s.track.placement.equals(PartConnections.target(head,PartConnections.Kind.TRACK)))return "";
        return switch(s.state){case PLAYING->"PLAYING";case PAUSED->"PAUSED";case FINISHED->"PLAYED";case STOPPED->"READY";};
    }
    public static boolean busyElsewhere(ServerLevel level,Placement head){
        var s=SESSIONS.get(level);return s!=null&&!s.track.placement.equals(PartConnections.target(head,PartConnections.Kind.TRACK))
            &&(s.state==HeadControlServices.Playback.PLAYING||s.state==HeadControlServices.Playback.PAUSED);
    }
    public static void configure(ServerLevel level,Placement head,double speed,boolean mirror){
        if(!Double.isFinite(speed)||speed<MusicQueueData.MIN_SPEED||speed>MusicQueueData.MAX_SPEED)throw new IllegalArgumentException("Invalid track speed");
        var s=SESSIONS.get(level);if(s==null||!s.track.placement.equals(PartConnections.target(head,PartConnections.Kind.TRACK)))return;
        var preview=new Session(s.chart,s.track,speed,mirror);
        preview.time=s.state==HeadControlServices.Playback.STOPPED?preview.initialTime():s.time;preview.frame();
        s.speed=speed;s.mirror=mirror;s.time=preview.time;s.clear();
        if(s.state==HeadControlServices.Playback.PLAYING||s.state==HeadControlServices.Playback.PAUSED)s.render(level);
    }
    public static void loadSong(ServerLevel level,PartConnections.Part track,SongLibrary.Chart song,double speed,boolean mirror)throws java.io.IOException{
        load(level,track,ChartDefinition.load(level,song.resource()),speed,mirror);
        var s=SESSIONS.get(level);s.songId=song.songId();s.difficulty=song.difficulty();s.audio=SongLibrary.audio(level,s.songId);s.syncAudio(level);
    }
    /** Load onto an existing registered track without generating or replacing blocks. */
    public static void load(ServerLevel level,PartConnections.Part track,ChartDefinition chart,double speed,boolean mirror){
        if(track.kind!=PartConnections.Kind.TRACK||!Double.isFinite(speed)||speed<MusicQueueData.MIN_SPEED||speed>MusicQueueData.MAX_SPEED
                ||PartConnections.integrity(level,track)!=PartConnections.Status.CONNECTED)
            throw new IllegalArgumentException("Invalid track or speed");
        var candidate=new Session(chart,track,speed,mirror);candidate.frame();
        unload(level);TrackMotion.stop(level);SESSIONS.put(level,candidate);candidate.publishScore(level);
    }
    /** Pure geometry check used by offline regression checks, without spawning entities. */
    static int checkGeometry(ChartDefinition chart,double speed,boolean mirror,double time){
        var frame=checkFrame(chart,speed,mirror,time);
        for(var spec:frame.values()){
            if(spec.item.endsWith("_body")){
                if(spec.x-spec.sx/2-Math.abs(spec.shear)/2<.5-1e-6
                        ||spec.x+spec.sx/2+Math.abs(spec.shear)/2>8.5+1e-6
                        ||spec.z-spec.sz/2< -1e-6||spec.z+spec.sz/2>24+1e-6)
                    throw new AssertionError("Ribbon leaves the track: "+spec);
            }
            if(spec.item.equals("speed_change_line")&&spec.bright)throw new AssertionError("Speed marker is fullbright");
            if(spec.item.equals("chart_air_body")||spec.item.startsWith("chart_effect_"))
                if(Math.abs(spec.sx-NoteDefinition.AIR_ACTION_GLOW_SIZE)>1e-8
                        ||Math.abs(spec.sy-NoteDefinition.AIR_ACTION_GLOW_SIZE)>1e-8||!spec.bright)
                    throw new AssertionError("Air and ALD glow must share a constant cross-section");
            if((spec.item.equals("chart_air_body")||spec.item.startsWith("chart_action_bar_"))
                    &&(Math.abs(spec.y-NoteDefinition.AIR_ACTION_HEIGHT)>1e-8||spec.lift!=0))
                throw new AssertionError("Air Action must remain one block above the deck");
        }
        return frame.size();
    }
    static Map<String,Spec> checkFrame(ChartDefinition chart,double speed,boolean mirror,double time){
        var track=new PartConnections.Part(PartConnections.Kind.TRACK,new Placement(net.minecraft.core.BlockPos.ZERO,net.minecraft.core.Direction.SOUTH),24);
        var session=new Session(chart,track,speed,mirror);session.time=time;return session.frame();
    }
    public static int control(CommandSourceStack source,HeadControlServices.Action action){
        var s=SESSIONS.get(source.getLevel());if(s==null)return missing(source);s.action(source.getLevel(),action);
        source.sendSuccess(()->Component.literal("谱面呈现："+s.state),false);return 1;
    }
    private static int select(CommandSourceStack source,String resource,double speed,boolean mirror,boolean start){
        var level=source.getLevel();
        var track=PartConnections.ofKind(level,PartConnections.Kind.TRACK).stream().min(Comparator.comparingDouble(part->{
            var box=new AABB(part.placement.point(0,0,0),part.placement.point(9,1,part.length));return box.distanceToSqr(source.getPosition());
        })).orElse(null);
        if(track==null||new AABB(track.placement.point(0,0,0),track.placement.point(9,1,track.length)).distanceToSqr(source.getPosition())>32*32){
            source.sendFailure(Component.literal("请靠近已生成的轨道；本命令不会替换地图。"));return 0;
        }
        if(PartConnections.integrity(level,track)!=PartConnections.Status.CONNECTED){source.sendFailure(Component.literal("轨道不完整或所在区块未加载。"));return 0;}
        try{
            var definition=ChartDefinition.load(level,resource);
            load(level,track,definition,speed,mirror);var session=SESSIONS.get(level);
            for(var song:SongLibrary.charts(level))if(song.resource().equals(resource)||resource.matches("\\d+")&&song.songId()==Integer.parseInt(resource)&&song.difficulty().equals("MASTER")){
                session.songId=song.songId();session.difficulty=song.difficulty();session.audio=SongLibrary.audio(level,session.songId);break;
            }
            if(start)session.action(level,HeadControlServices.Action.START_OR_RESUME);
            source.sendSuccess(()->Component.literal("已加载 "+definition.title()+" "+session.difficulty+"，"+speed+" 格/秒，镜像="+mirror
                +"。预滚后按音乐时间播放，两侧边框分别发出玩家视角的左右声道；暂不计分。"),false);return 1;
        }catch(Exception error){source.sendFailure(Component.literal("谱面加载失败："+error.getMessage()));return 0;}
    }
    public static AutoScoreTimeline.Snapshot score(ServerLevel level){var s=SESSIONS.get(level);if(s==null)return null;if(s.scoring==null)s.scoring=AutoScoreTimeline.of(s.chart);return s.scoring.at(s.time);}
    record Spec(String item,double x,double y,double z,double sx,double sy,double sz,double shear,double lift,boolean bright,int profile){}
    record NativeFrame(double time,Placement track,Map<String,Spec> specs,Map<String,net.minecraft.world.phys.Vec3> positions){}
    static NativeFrame nativeFrame(ServerLevel level){
        var s=SESSIONS.get(level);if(s==null)return null;
        Map<String,Spec> specs=new HashMap<>();Map<String,net.minecraft.world.phys.Vec3> positions=new HashMap<>();
        for(var e:s.entities.entrySet())if(!s.retiring.containsKey(e.getKey())&&!e.getValue().isRemoved()){
            specs.put(e.getKey(),s.lastSpecs.get(e.getKey()));positions.put(e.getKey(),e.getValue().position());
        }
        return new NativeFrame(s.time,s.track.placement,Map.copyOf(specs),Map.copyOf(positions));
    }
    private static final class Session {
        final ChartDefinition chart;final PartConnections.Part track;double speed;boolean mirror;
        AutoScoreTimeline scoring;HeadDisplayData publishedScore;
        int activeProfile;
        int songId;String difficulty="MASTER";
        SongLibrary.Audio audio;
        NoteCueAudio.Transport noteCues;
        final long audioSession=++nextAudioSession;long audioRevision;
        final Map<String,Display.ItemDisplay> entities=new HashMap<>();
        final Map<String,Spec> lastSpecs=new HashMap<>();
        final Map<String,Long> retiring=new HashMap<>();
        double time;HeadControlServices.Playback state=HeadControlServices.Playback.STOPPED;
        Session(ChartDefinition chart,PartConnections.Part track,double speed,boolean mirror){
            this.chart=chart;this.track=track;this.speed=speed;this.mirror=mirror;time=initialTime();
        }
        double initialTime(){return -Math.max(1000,(RhythmTiming.judgment(track.length)-.25)/speed*1000);}
        void publishScore(ServerLevel level){
            if(scoring==null)scoring=AutoScoreTimeline.of(chart);
            var data=scoring.at(time).display();if(data.equals(publishedScore))return;
            var headPlacement=new Placement(track.placement.block(-1,0,-1),track.placement.forward());
            var head=PartConnections.find(level,PartConnections.Kind.HEAD,headPlacement);
            if(head==null||PartConnections.integrity(level,head)!=PartConnections.Status.CONNECTED)return;
            TrackHeadDisplay.updateScore(level,headPlacement,data);publishedScore=data;
        }
        void clear(){for(var entity:entities.values()){LIVE.remove(entity.getUUID());entity.discard();}entities.clear();lastSpecs.clear();retiring.clear();}
        void action(ServerLevel level,HeadControlServices.Action action){switch(action){
            case STOP->{clear();state=HeadControlServices.Playback.STOPPED;time=initialTime();}
            case PAUSE->{if(state==HeadControlServices.Playback.PLAYING)state=HeadControlServices.Playback.PAUSED;}
            case START_OR_RESUME->{if(state==HeadControlServices.Playback.FINISHED)time=initialTime();state=HeadControlServices.Playback.PLAYING;render(level);}
        }publishScore(level);audioRevision++;syncAudio(level);}
        void syncAudio(ServerLevel level){
            if(noteCues==null)noteCues=new NoteCueAudio.Transport(chart);
            noteCues.sync(level,track,audioSession,audioRevision,time,state==HeadControlServices.Playback.PLAYING);
            if(audio==null)return;
            var p=track.placement;var o=p.origin();
            RailAudio.send(level,new RailAudio.Snapshot(RailAudio.dimension(level),audioSession,audioRevision,audio.event(),audio.resource(),
                    o.getX(),o.getY(),o.getZ(),p.forward().get2DDataValue(),track.length,time,audio.offset(),audio.duration(),state==HeadControlServices.Playback.PLAYING));
        }
        void tick(ServerLevel level){if(state!=HeadControlServices.Playback.PLAYING)return;time+=50;render(level);publishScore(level);
            if(time>Math.max(chart.lastTimeMs()+Math.max(1000,track.length/speed*1000),audio==null?0:audio.duration()+audio.offset())){clear();state=HeadControlServices.Playback.FINISHED;
                audioRevision++;syncAudio(level);
                if(songId>0)MusicQueue.finished(level,track,songId,difficulty);}}
        double z(double t,boolean marker){return chart.position(t,time,speed,track.length,marker,activeProfile);}
        void cap(Map<String,Spec> frame,String key,String item,double t,double lane,double width,double height,boolean marker){
            cap(frame,key,item,t,lane,width,height,marker,1);
        }
        void cap(Map<String,Spec> frame,String key,String item,double t,double lane,double width,double height,boolean marker,double widthScale){
            double z=z(t,marker),depth=marker||item.equals("beat_line")?.03125:.5,lo=Math.max(0,z-depth/2),hi=Math.min(track.length,z+depth/2);
            if(hi<=lo)return;double left=mirror?16-lane-width:lane;
            frame.put(key,new Spec(item,.5+(left+width/2)*.5,height,(lo+hi)/2,widthScale,1,(hi-lo)/depth,0,0,!marker,activeProfile));
        }
        void actionBar(Map<String,Spec> frame,String key,double t,double lane,double width){
            double barWidth=Math.max(2,width),left=Math.clamp(lane+(width-barWidth)/2,0,16-barWidth);
            cap(frame,key,"chart_action_bar_"+(int)barWidth,t,left,barWidth,NoteDefinition.AIR_ACTION_HEIGHT,false);
        }
        String arrow(ChartDefinition.Note n){
            String variant=n.airVariant(mirror);
            return "chart_air_arrow_"+(variant.equals("up")?"":variant+"_")+(int)n.width();
        }
        void ribbon(Map<String,Spec> frame,ChartDefinition.Note n,String item,double height,boolean air){
            // Split at every speed boundary BEFORE culling: reverse effects can
            // bring the middle of a path on screen while both ends are outside.
            TreeSet<Double> samples=new TreeSet<>();
            samples.add(n.time());samples.add(n.end());
            for(var effect:chart.speeds(n.scrollProfile())){if(effect.start()>n.time()&&effect.start()<n.end())samples.add(effect.start());if(effect.end()>n.time()&&effect.end()<n.end())samples.add(effect.end());}
            Double previous=null;int index=0;
            boolean spatial=Set.of("ASC","ASD","ALD").contains(n.type());
            boolean thinLine=air||n.type().equals("ALD");
            for(double next:samples){if(previous==null){previous=next;continue;}double a=previous,b=next;previous=next;
                double aZ=z(a,false),bZ=z(b,false);if(Math.abs(aZ-bZ)<1e-6)continue;
                double lo=Math.max(0,Math.min(aZ,bZ)),hi=Math.min(track.length,Math.max(aZ,bZ));if(hi<=lo)continue;
                double tLo=a+(b-a)*(lo-aZ)/(bZ-aZ),tHi=a+(b-a)*(hi-aZ)/(bZ-aZ);
                // A sheared cube represents a constant-width linear segment
                // exactly, including its vertical slope. Only tapering needs
                // subdivision; dense decoration must not multiply by length.
                int steps=Math.max(1,(int)Math.ceil(Math.abs(n.widthAt(tHi)-n.widthAt(tLo))*4));
                // Air and decoration follow the source centreline at constant
                // thickness. Source lane width is not the width of their glow.
                if(thinLine)steps=1;
                // Dense control paths can change width many times within a few
                // pixels. Keep every source segment, but do not subdivide its
                // taper beyond 1/16 block along the visible track.
                steps=Math.min(steps,Math.max(1,(int)Math.ceil((hi-lo)*16)));
                for(int step=0;step<steps;step++){
                    double u=(double)step/steps,v=(double)(step+1)/steps;
                    double ta=tLo+(tHi-tLo)*u,tb=tLo+(tHi-tLo)*v;
                    double wa=n.widthAt(ta),wb=n.widthAt(tb);
                    double ca=.5+(n.laneAt(ta,mirror)+wa/2)*.5,cb=.5+(n.laneAt(tb,mirror)+wb/2)*.5;
                    double ya=air?NoteDefinition.AIR_ACTION_HEIGHT:spatial?n.heightAt(ta)*.2:height,
                        yb=air?NoteDefinition.AIR_ACTION_HEIGHT:spatial?n.heightAt(tb)*.2:height;
                    double width=thinLine?NoteDefinition.AIR_ACTION_GLOW_SIZE:Math.min(wa,wb)*.5;
                    // Narrow only ground sustain bodies, keeping their source centreline and path shear.
                    if(item.equals("chart_hold_body")||item.equals("chart_slide_body"))width*=SUSTAIN_WIDTH_SCALE;
                    frame.put(n.id()+"_body_"+index++,new Spec(item,(ca+cb)/2,(ya+yb)/2,lo+(hi-lo)*(u+v)/2,width,
                        thinLine?NoteDefinition.AIR_ACTION_GLOW_SIZE:.018,(hi-lo)/steps,cb-ca,yb-ya,true,activeProfile));
                }
            }
        }
        Map<String,Spec> frame(){
            activeProfile=0;
            Map<String,Spec> frame=new LinkedHashMap<>();
            for(int i=0;i<chart.beats().size();i++)cap(frame,"beat_"+i,"beat_line",chart.beats().get(i),0,16,.006,false);
            for(var n:chart.notes()){
                activeProfile=n.scrollProfile();
                int width=(int)n.width();String prefix=n.id();
                switch(n.type()){
                    case "MNE"->cap(frame,prefix,"mine_"+width,n.time(),n.lane(),n.width(),.025,false);
                    case "TAP","CHR","FLK"->cap(frame,prefix,(n.type().equals("TAP")?"tap_":n.type().equals("CHR")?"ex_tap_":"flick_")+width,n.time(),n.lane(),n.width(),.025,false);
                    case "AIR","AUL","AUR","ADW","ADL","ADR"->cap(frame,prefix,arrow(n),n.time(),n.lane(),n.width(),.025,false);
                    case "HLD","HXD"->{ribbon(frame,n,"chart_hold_body",.04,false);cap(frame,prefix+"_head","ex_tap_"+width,n.time(),n.lane(),n.width(),.025,false);
                        cap(frame,prefix+"_end","ex_tap_"+width,n.end(),n.endLane(),n.endWidth(),.025,false);}
                    case "SLD","SLC","SXD","SXC"->{ribbon(frame,n,"chart_slide_body",.045,false);
                        if(n.root()||n.type().endsWith("D"))cap(frame,prefix+"_node",(n.root()&&n.type().startsWith("SX")?"ex_tap_":"chart_slide_node_")+width,n.time(),n.lane(),n.width(),.03,false,n.root()?1:SUSTAIN_WIDTH_SCALE);
                        if(n.terminal())cap(frame,prefix+"_tail","chart_slide_node_"+(int)n.endWidth(),n.end(),n.endLane(),n.endWidth(),.03,false);}
                    case "AHD","AHX"->{ribbon(frame,n,"chart_air_body",1.0,true);
                        if(n.type().equals("AHD")&&!n.target().equals("AHD")&&!n.target().equals("AHX"))cap(frame,prefix+"_arrow","chart_air_arrow_"+width,n.time(),n.lane(),n.width(),.025,false);
                        actionBar(frame,prefix+"_action_start",n.time(),n.lane(),n.width());
                        actionBar(frame,prefix+"_action_end",n.end(),n.endLane(),n.endWidth());}
                    case "ASC","ASD"->{ribbon(frame,n,"chart_air_body",1,true);
                        if(n.root())cap(frame,prefix+"_arrow",arrow(n),n.time(),n.lane(),n.width(),.025,false);
                        if(n.root()||n.type().equals("ASD"))actionBar(frame,prefix+"_action_start",n.time(),n.lane(),n.width());
                        if(n.terminal())actionBar(frame,prefix+"_action_end",n.end(),n.endLane(),n.endWidth());}
                    case "ALD"->{
                        if(n.color().equals("NON"))actionBar(frame,prefix+"_effect_action",n.time(),n.lane(),n.width());
                        else ribbon(frame,n,"chart_effect_"+n.color().toLowerCase(Locale.ROOT)+"_body",1,false);
                    }
                    default->throw new IllegalArgumentException("Unsupported note");
                }
            }
            for(int i=0;i<chart.markers().size();i++)cap(frame,"speed_"+i,"speed_change_line",chart.markers().get(i),0,16,.03,true);
            if(frame.size()>4096)throw new IllegalArgumentException("Visible chart exceeds 4096 display entities: "+chart.title()+" at "+time+" ms, speed "+speed+", count "+frame.size()+"; shorten the track or increase speed");
            return frame;
        }
        void render(ServerLevel level){
            var frame=frame();var remove=entities.entrySet().iterator();while(remove.hasNext()){
                var entry=remove.next();
                if(frame.containsKey(entry.getKey())&&!entry.getValue().isRemoved()){retiring.remove(entry.getKey());continue;}
                long reached=retiring.computeIfAbsent(entry.getKey(),key->level.getGameTime());
                if(!entry.getValue().isRemoved()&&level.getGameTime()-reached<RhythmTiming.ENDPOINT_GRACE_TICKS){
                    var last=lastSpecs.get(entry.getKey());double depth=last.item.endsWith("_body")?last.sz:(last.item.equals("speed_change_line")||last.item.equals("beat_line")?.03125:.5)*last.sz;
                    double edge=last.z<track.length/2.0?depth/2:track.length-depth/2;
                    var end=track.placement.point(last.x,TrackBlock.SURFACE_HEIGHT+last.y,edge);
                    entry.getValue().setPos(end.x,end.y,end.z);continue;
                }
                LIVE.remove(entry.getValue().getUUID());entry.getValue().discard();lastSpecs.remove(entry.getKey());retiring.remove(entry.getKey());remove.remove();}
            for(var entry:frame.entrySet()){
                var s=entry.getValue();var entity=entities.get(entry.getKey());boolean fresh=entity==null;
                if(fresh){entity=EntityTypes.ITEM_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);if(entity==null)throw new IllegalStateException("Cannot create chart display");
                    entity.setItemStack(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,s.item))));
                    entity.setItemTransform(ItemDisplayContext.FIXED);entity.setYRot(track.placement.yaw());
                    entity.setPosRotInterpolationDuration(1);entity.setTransformationInterpolationDuration(1);
                    if(s.bright)entity.setBrightnessOverride(new Brightness(15,15));
                    entity.setWidth(10);entity.setHeight(4);entity.addTag(TAG);LIVE.add(entity.getUUID());entities.put(entry.getKey(),entity);
                }
                var pos=track.placement.point(s.x,TrackBlock.SURFACE_HEIGHT+s.y,s.z);entity.setPos(pos.x,pos.y,pos.z);
                entity.setTransformation(new Transformation(new Matrix4f().scaling((float)s.sx,(float)s.sy,(float)s.sz).m20((float)s.shear).m21((float)s.lift)));
                entity.setTransformationInterpolationDelay(0);
                lastSpecs.put(entry.getKey(),s);
                if(fresh)level.addFreshEntity(entity);
            }
        }
    }
}
