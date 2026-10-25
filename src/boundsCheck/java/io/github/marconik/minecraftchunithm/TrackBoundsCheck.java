package io.github.marconik.minecraftchunithm;

/** Exhaustively checks 16 widths at every half-lane slot, including negative world coordinates. */
public final class TrackBoundsCheck {
    public static void main(String[] args) {
        NoteCueCheck.run();
        int checked = 0;
        for (double min : new double[] {-100.5, 0.5, 200.5}) {
            for (boolean alongZ : new boolean[] {false, true}) {
                var bounds = new TrackBounds(min, min + 8, alongZ);
                for (int width = 1; width <= 16; width++) {
                    for (int slot = -1; slot <= 17; slot++) {
                        boolean expected = slot >= 0 && slot + width <= 16;
                        boolean actual = bounds.contains(min + slot * 0.5, width * 0.5);
                        if (expected != actual) throw new AssertionError("Boundary mismatch: " + bounds
                                + ", width=" + width + ", slot=" + slot);
                        checked++;
                    }
                }
            }
        }
        System.out.println("TAP boundary checks passed: " + checked);
        if(PanelMarquee.offset(100,0)!=0||PanelMarquee.offset(1,100)!=0
                ||PanelMarquee.offset(2,160)!=40||PanelMarquee.offset(4,160)!=160
                ||PanelMarquee.offset(6,160)!=80)throw new AssertionError("Song marquee timing or endpoint pause mismatch");
        for(int i=0;i<10000;i++)for(double overflow:new double[]{1,100,160,5000}){
            double offset=PanelMarquee.offset(i*.031,overflow);
            if(offset<0||offset>overflow)throw new AssertionError("Song marquee leaves its viewport range");
        }
        System.out.println("Song metadata marquee: fitting text, endpoint pauses, return motion and clipping range passed.");
        for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL){
            var track=new Placement(new net.minecraft.core.BlockPos(-17,63,43),facing);
            for(double along:new double[]{-10,0,12,24,40}){
                var listener=track.point(4.5,2,along);
                var left=RailAudioGeometry.position(track,24,listener,true);
                var right=RailAudioGeometry.position(track,24,listener,false);
                double z=Math.clamp(along,0,24);
                if(!left.equals(track.point(.25,.75,z))||!right.equals(track.point(8.75,.75,z)))throw new AssertionError("Rail emitter escaped its edge");
                var playerRight=facing.getOpposite().getClockWise();var difference=right.subtract(left);
                if(difference.x*playerRight.getStepX()+difference.z*playerRight.getStepZ()<=0)throw new AssertionError("Stereo is reversed from the player's viewpoint");
            }
        }
        System.out.println("Rail stereo geometry: player-relative left/right, nearest points and endpoints passed in four facings.");
        double[] responses=new double[16];responses[0]=Double.NaN;responses[1]=2;responses[2]=-.5;
        var input=new HeadControlServices.Input(true,false,true,Double.POSITIVE_INFINITY,.6,responses);
        responses[1]=0;double[] copy=input.ground();copy[1]=0;
        if(input.leftAir()!=0||input.ground()[0]!=0||input.ground()[1]!=1||input.ground()[2]!=0)
            throw new AssertionError("Device responses must be bounded and immutable");
        for(var state:HeadControlServices.Playback.values())for(var action:HeadControlServices.Action.values()){
            if(HeadControlServices.allowed(action,new HeadControlServices.Transport(false,state))!=(action!=HeadControlServices.Action.START_OR_RESUME))
                throw new AssertionError("No-chart state must disable only Start, retaining Pause and Stop");
            boolean expected=switch(action){case START_OR_RESUME->state!=HeadControlServices.Playback.PLAYING;
                case PAUSE,STOP->true;};
            if(HeadControlServices.allowed(action,new HeadControlServices.Transport(true,state))!=expected)
                throw new AssertionError("Playback button state mismatch");
        }
        try{new HeadControlServices.Input(false,false,false,0,0,new double[8]);throw new AssertionError("Wrong lane count accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("Head UI input validation and playback availability checks passed.");
        if(NameplateTypography.width("Player",false)!=23||NameplateTypography.width("Player",true)!=26
                ||NameplateTypography.width("欢迎来到中二节奏",false)!=72)
            throw new AssertionError("Uniform font alignment metrics mismatch");
        for(var difficulty:SongDisplayData.Difficulty.values()) {
            var song=new SongDisplayData(SongDisplayData.defaults().coverModel(),"曲目","作者",3,difficulty,"15+",8,true);
            if(song.track()!=3||!song.mirror()||song.speed()!=8)throw new AssertionError("Song interface lost inputs");
        }
        try {new SongDisplayData(SongDisplayData.defaults().coverModel(),"A","B",0,SongDisplayData.Difficulty.MASTER,"15",8,false);
            throw new AssertionError("Invalid track accepted");}catch(IllegalArgumentException expected) { }
        var standing=new net.minecraft.core.BlockPos(-7,63,-9);
        for(var color:NameDisplayData.TitleColor.values()){
            var data=new NameDisplayData("TEAM","称号",color,"99","玩家",NameDisplayData.defaults().characterModel(),"16.50");
            if(data.titleColor()!=color||!data.name().equals("玩家")||!data.rating().equals("16.50"))
                throw new AssertionError("Nameplate interface lost inputs");
        }
        try {new NameDisplayData("A","B",null,"1","C",NameDisplayData.defaults().characterModel(),"0");
            throw new AssertionError("Missing title color accepted");}catch(IllegalArgumentException expected) { }
        for(var direction:new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.EAST}) {
            var track=new Placement(standing.relative(direction.getCounterClockWise(),-4),direction);
            if(!track.block(4,0,0).equals(standing))throw new AssertionError("Track anchor differs from standing block");
            var groundStandalone=new Placement(standing.relative(direction.getCounterClockWise(),-GroundSlide.WIDTH/2).relative(direction,-1),direction);
            if(!groundStandalone.block(6,0,1).equals(standing))throw new AssertionError("Ground Slide anchor differs from standing block");
            var groundConnected=PartConnections.groundTarget(track,24);
            var leftAir=AirSensors.target(groundConnected,true);
            var rightAir=AirSensors.target(groundConnected,false);
            if(!leftAir.block(4,0,0).relative(direction.getCounterClockWise()).equals(groundConnected.block(0,0,0))
                    ||!rightAir.block(0,0,0).equals(groundConnected.block(13,0,0)))
                throw new AssertionError("AIR bases must touch opposite Ground Slide sides");
            for(boolean left:new boolean[]{true,false}){
                int blocks=0;
                for(int y=0;y<5;y++)for(int x=0;x<5;x++)for(int z=0;z<3;z++){
                    boolean expected=y==0||x==(left?0:4);
                    if(AirSensors.occupied(left,x,y)!=expected)throw new AssertionError("AIR panel must occupy only the outermost base row");
                    if(expected)blocks++;
                }
                if(blocks!=27)throw new AssertionError("AIR part must contain a 5x3x1 base and 3x4x1 panel");
            }
            for(int port=0;port<5;port++){
                if(AirSensors.portHeight(port)!=2+port*.5)throw new AssertionError("AIR IR sensor height differs from the standing surface");
                var midpoint=AirSensors.portPoint(groundConnected,true,port).add(AirSensors.portPoint(groundConnected,false,port)).scale(.5);
                if(!midpoint.equals(groundConnected.point(6.5,AirSensors.portHeight(port),1.5)))
                    throw new AssertionError("AIR IR sensors must face one another at mirrored coordinates");
                for(boolean left:new boolean[]{true,false}){
                    var sensor=AirSensors.target(groundConnected,left);
                    var area=AirSensors.sensingArea(sensor,left,port);
                    var center=area.getCenter();
                    var contact=new net.minecraft.world.phys.AABB(center.x-.01,center.y-.01,center.z-.01,
                            center.x+.01,center.y+.01,center.z+.01);
                    var signal=AirSensors.contacts(sensor,left,java.util.List.of(contact));
                    if(signal.mask()!=(1<<port)||signal.strength()!=(port+1)/5.0)
                        throw new AssertionError("AIR height channel or strength mismatch in "+direction);
                    if(AirSensors.contacts(AirSensors.target(groundConnected,!left),!left,java.util.List.of(contact)).mask()!=0)
                        throw new AssertionError("AIR response leaked into the opposite side");
                    if(AirSensors.contacts(sensor,left,java.util.List.of()).mask()!=0)
                        throw new AssertionError("AIR release must clear all height channels");
                    var outside=contact.move(direction.getStepX(),0,direction.getStepZ());
                    if(AirSensors.contacts(sensor,left,java.util.List.of(outside)).mask()!=0)
                        throw new AssertionError("AIR must ignore entities outside the aperture depth");
                }
            }
            if(!groundConnected.block(0,0,0).equals(track.block(-2,0,24))
                    ||!groundConnected.block(12,0,2).equals(track.block(10,0,26)))
                throw new AssertionError("Ground Slide must begin immediately after the track end");
            if(!groundConnected.point(2.5,.5,.5).equals(track.point(.5,.5,24.5))
                    ||!groundConnected.point(10.5,.5,.5).equals(track.point(8.5,.5,24.5)))
                throw new AssertionError("Wider Ground Slide cabinet must preserve both 8-block light-window boundaries");
            for(int lane=0;lane<16;lane++)for(int cell=0;cell<4;cell++){
                var contact=groundConnected.point(2.75+lane*.5,.75,.75+cell*.5);
                var box=new net.minecraft.world.phys.AABB(contact.x-.05,contact.y,contact.z-.05,contact.x+.05,contact.y+.1,contact.z+.05);
                if(GroundSlidePressure.contacts(groundConnected,java.util.List.of(box))!=(1<<lane))
                    throw new AssertionError("Every half-block cell must activate its whole lane in all four directions");
            }
            var boundary=groundConnected.point(3,.75,1.5);
            var crossing=new net.minecraft.world.phys.AABB(boundary.x-.05,boundary.y,boundary.z-.05,boundary.x+.05,boundary.y+.1,boundary.z+.05);
            if(GroundSlidePressure.contacts(groundConnected,java.util.List.of(crossing))!=3)
                throw new AssertionError("Contact spanning a boundary must activate both adjacent strips");
            var rim=groundConnected.point(1,1,1.5);
            var above=groundConnected.point(3.25,1,1.5);
            for(var contact:new net.minecraft.world.phys.Vec3[]{rim,above}){
                var box=new net.minecraft.world.phys.AABB(contact.x-.05,contact.y,contact.z-.05,contact.x+.05,contact.y+.1,contact.z+.05);
                if(GroundSlidePressure.contacts(groundConnected,java.util.List.of(box))!=0)
                    throw new AssertionError("Rim or elevated contact must not activate touch strips");
            }
            if(GroundSlidePressure.contacts(groundConnected,java.util.List.of())!=0)
                throw new AssertionError("Released controller must return to yellow");
            var front=direction.getOpposite();
            var playerBlock=standing.above();
            var nameplate=NamePanel.placement(playerBlock,direction);
            var anchor=playerBlock.relative(direction);
            if(!nameplate.block(6,0,0).equals(anchor)||nameplate.forward()!=front)
                throw new AssertionError("Nameplate must face player with lower-right anchor ahead");
            var song=new Placement(anchor,front);
            for(int x=0;x<7;x++)for(int y=0;y<3;y++){
                if(!nameplate.block(6-x,y,0).equals(anchor.relative(song.across(),-x).above(y)))
                    throw new AssertionError("Nameplate is not the mirror of the song panel");
            }
            var head=new Placement(standing.relative(direction).relative(front.getCounterClockWise(),-5),front);
            var connectedTrack=PartConnections.target(head,PartConnections.Kind.TRACK);
            for(int x:new int[]{0,4,8}){
                if(!connectedTrack.block(x,0,0).equals(head.block(x+1,0,1)))
                    throw new AssertionError("Three track sockets must align with the head pedestal");
            }
            var connectedPlayer=PartConnections.target(head,PartConnections.Kind.PLAYER);
            var connectedSong=PartConnections.target(head,PartConnections.Kind.SONG);
            if(!connectedPlayer.block(6,0,0).relative(head.across()).equals(head.block(0,1,0))
                    ||!connectedSong.block(0,0,0).equals(head.block(11,1,0)))
                throw new AssertionError("Side panels must touch head sides and align vertically");
            var nameSource=head.block(-1,1,0).relative(front);
            if(!NamePanel.placement(nameSource,front.getOpposite()).equals(connectedPlayer))
                throw new AssertionError("Assembly differs from normal nameplate generation");
            if(!head.block(5,0,0).equals(standing.relative(direction)))throw new AssertionError("Head anchor is not one block ahead");
            if(!track.point(4.5,0,.5).equals(new net.minecraft.world.phys.Vec3(standing.getX()+.5,63,standing.getZ()+.5)))
                throw new AssertionError("Display frame differs from block frame");
        }
        if(GroundSlide.WIDTH!=13||GroundSlide.DEPTH!=3||GroundSlide.LIGHT_WIDTH!=8
                ||GroundSlide.LIGHT_DEPTH!=2||GroundSlide.LANES!=16||GroundSlide.HEIGHT!=1||GroundSlide.SURFACE_HEIGHT!=.75)
            throw new AssertionError("Ground Slide dimensions changed");
        double recessedArea=0;
        for(int z=0;z<GroundSlide.DEPTH;z++)for(int x=0;x<GroundSlide.WIDTH;x++){
            var recess=GroundSlide.recess(x,z);
            boolean expected=x>=2&&x<=10;
            if((recess!=null)!=expected)throw new AssertionError("Wrong recessed tile extent");
            if(recess!=null){
                if(recess.minX()<0||recess.maxX()>16||recess.minZ()<0||recess.maxZ()>16)
                    throw new AssertionError("Recess collision bounds exceed tile");
                recessedArea+=(recess.maxX()-recess.minX())*(recess.maxZ()-recess.minZ());
            }
        }
        if(recessedArea!=8*2*16*16)throw new AssertionError("Recess collision footprint must remain exactly 8x2 blocks");
        if(GroundSlidePressure.tileMask(2,1)!=2||GroundSlidePressure.tileMask(10,1<<15)!=1
                ||GroundSlidePressure.tileMask(3,6)!=3||GroundSlidePressure.tileMask(0,65535)!=0
                ||GroundSlidePressure.tileMask(12,65535)!=0)
            throw new AssertionError("Pressed tile halves must preserve adjacent lanes and leave the rim unchanged");
        System.out.println("Ground Slide pressure: 256 oriented cell contacts, adjacent lanes, rim/height exclusion, release and tile masks passed.");
        long[] scores={0,100999,101000,302999,303000,403999,404000,434300,1009999,1010000,2000000};
        for(long score:scores) {
            var data=new HeadDisplayData(1,2,3,4,5,score);
            long capped=Math.min(score,1010000);
            if(data.filledCells()!=capped/101000)throw new AssertionError("Wrong score completion cells");
            if(Math.abs((data.filledCells()+data.barFraction())*101000-capped)>1e-6)throw new AssertionError("Wrong score remainder");
            if(data.goldBar()!=(score>=303000))throw new AssertionError("Wrong colour threshold");
            if(Math.abs(data.percent()-capped/10100.0)>1e-9)throw new AssertionError("Wrong percent");
        }
        try {new HeadDisplayData(0,0,0,0,0,-1);throw new AssertionError("Negative score accepted");}
        catch(IllegalArgumentException expected) { }
        for(var connection:NoteDefinition.Connection.values()) {
            var path=new NoteDefinition("curve",NoteDefinition.Kind.SLIDE,0,0,2,1000,
                    NoteDefinition.Direction.NONE,"",java.util.List.of(
                    new NoteDefinition.Node(0,0,2,0,connection),new NoteDefinition.Node(1000,14,2,1)));
            if(path.sample(0).lane()!=0 || path.sample(1000).lane()!=14)throw new AssertionError("Curve endpoint mismatch");
            double expected=connection==NoteDefinition.Connection.LINEAR?7:
                    connection==NoteDefinition.Connection.PARABOLA_UP?10.5:3.5;
            if(path.sample(500).lane()!=expected)throw new AssertionError("Wrong quadratic interpolation");
            for(int time=0;time<=1000;time++) {
                var node=path.sample(time);
                if(node.lane()<0 || node.lane()+node.width()>16)throw new AssertionError("Curve exceeds track");
            }
        }
        for (var kind:NoteDefinition.Kind.values()) for (int width=1;width<=16;width++) {
            if(kind==NoteDefinition.Kind.AIR_ACTION && width==1)continue;
            if(kind==NoteDefinition.Kind.SPEED_CHANGE && width!=16)continue;
            var definition=NoteDefinition.preview(kind,width);
            if (definition.width()!=width || definition.kind()!=kind) throw new AssertionError("Invalid note attributes");
        }
        var speedMarker=NoteDefinition.preview(NoteDefinition.Kind.SPEED_CHANGE,16);
        if(speedMarker.judged() || speedMarker.lane()!=0 || speedMarker.durationMs()!=0
                || !speedMarker.nodes().isEmpty())throw new AssertionError("Speed marker must be visual only");
        if(!NoteDefinition.preview(NoteDefinition.Kind.TAP,4).judged())
            throw new AssertionError("Speed markers changed normal note judgment");
        try {
            NoteDefinition.preview(NoteDefinition.Kind.SPEED_CHANGE,8);
            throw new AssertionError("Partial-width speed marker accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            new NoteDefinition.Node(0,15,2,0);
            throw new AssertionError("Out-of-bounds path accepted");
        } catch (IllegalArgumentException expected) { }
        for (int beat=0;beat<10000;beat++) {
            if (RhythmTiming.beatTime(beat,120) != beat*0.5) throw new AssertionError("BPM drift");
        }
        if (RhythmTiming.position(0,1,10)-RhythmTiming.position(0,0,10) != 10)
            throw new AssertionError("Incorrect blocks/second");
        for (int length : new int[] {2,24,128}) {
            double atLine=(RhythmTiming.judgment(length)-0.25)/10;
            if (RhythmTiming.expired(0,atLine,10,length)
                    || RhythmTiming.expired(0,atLine+0.025,10,length))
                throw new AssertionError("Notes must survive passing the judgment line");
            double arrival=(length-0.25)/10;
            if (RhythmTiming.expired(0,arrival-0.0001,10,length)
                    || !RhythmTiming.expired(0,arrival+0.0001,10,length))
                throw new AssertionError("Incorrect disappearance time");
        }
        var rng=new java.util.Random(120);
        // Simulate the actual discrete update/removal sequence, not only ideal arrival times.
        for (double speed : new double[] {3,10,20,30}) for (int length : new int[] {2,24,128}) {
            long arrived = -1;
            int endpointUpdates = 0;
            boolean removed = false;
            for (long tick = 0; tick < 2000; tick++) {
                if (RhythmTiming.canRemove(arrived,tick)) {
                    if (endpointUpdates < 2) throw new AssertionError("Removed before endpoint was displayed");
                    removed = true;
                    break;
                }
                double position = RhythmTiming.displayPosition(0,tick/20.0,speed,length);
                if (position > length) throw new AssertionError("Endpoint overshoot");
                if (position == length) endpointUpdates++;
                if (arrived < 0 && RhythmTiming.expired(0,tick/20.0,speed,length)) arrived=tick;
            }
            if (!removed) throw new AssertionError("Endpoint entity leaked");
        }
        System.out.println("Discrete endpoint delivery and delayed removal checks passed at 3, 10 and 20 blocks/second.");
        for (int i=0;i<10000;i++) {
            int width=rng.nextInt(16)+1, lane=rng.nextInt(17-width);
            if (!new TrackBounds(0.5,8.5,false).contains(0.5+lane*0.5,width*0.5))
                throw new AssertionError("Random TAP exceeded borders");
        }
        System.out.println("120 BPM / 10 blocks per second timing, despawn and random widths passed.");
        ChartPlaybackCheck.run();
        MusicQueueCheck.run();
        HeadTerminalCheck.run();
    }
}
