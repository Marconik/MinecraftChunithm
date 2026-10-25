package io.github.marconik.minecraftchunithm;

import java.util.List;

/** Chart attributes in milliseconds and half-block lanes; independent of model geometry. */
public record NoteDefinition(String id, Kind kind, double timeMs, double lane, double width,
                             double durationMs, Direction direction, String parentId, List<Node> nodes) {
    public enum Kind { TAP, EX_TAP, HOLD, SLIDE, FLICK, AIR, AIR_ACTION, SPEED_CHANGE }
    public static final double AIR_ACTION_LINE_SIZE = 0.075;
    public static final double AIR_ACTION_HEIGHT = 1.0;
    public static final double AIR_ACTION_GLOW_SIZE = 0.30;
    public static final double AIR_ACTION_MIN_BAR_LANES = 2;
    public enum Direction { NONE, ANY, LEFT, RIGHT, UP, DOWN, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT }
    public enum Connection {
        LINEAR, PARABOLA_UP, PARABOLA_DOWN;
        public double progress(double t) {
            t=Math.max(0,Math.min(1,t));
            return switch(this) { case LINEAR -> t; case PARABOLA_UP -> 2*t-t*t; case PARABOLA_DOWN -> t*t; };
        }
    }
    /** Visual timing markers never participate in hit detection, combo or score. */
    public boolean judged() { return kind != Kind.SPEED_CHANGE; }
    /** connection controls the segment from this node to the next node. */
    public record Node(double offsetMs, double lane, double width, double height, Connection connection) {
        public Node(double offsetMs,double lane,double width,double height) {
            this(offsetMs,lane,width,height,Connection.LINEAR);
        }
        public Node {
            if (connection==null) throw new IllegalArgumentException("Missing segment connection");
            if (!Double.isFinite(offsetMs) || offsetMs < 0 || !Double.isFinite(height) || height < 0)
                throw new IllegalArgumentException("Invalid path time or height");
            checkLane(lane,width);
        }
    }
    public Node sample(double offsetMs) {
        if (!Double.isFinite(offsetMs) || nodes.isEmpty()) throw new IllegalArgumentException("Cannot sample this path");
        if (offsetMs<=0) return nodes.getFirst();
        if (offsetMs>=durationMs) return nodes.getLast();
        for (int i=1;i<nodes.size();i++) {
            Node a=nodes.get(i-1), b=nodes.get(i);
            if (offsetMs>b.offsetMs) continue;
            double u=a.connection.progress((offsetMs-a.offsetMs)/(b.offsetMs-a.offsetMs));
            return new Node(offsetMs,a.lane+(b.lane-a.lane)*u,a.width+(b.width-a.width)*u,
                    a.height+(b.height-a.height)*u);
        }
        return nodes.getLast();
    }
    private static void checkLane(double lane,double width) {
        if (!Double.isFinite(lane) || !Double.isFinite(width) || lane < 0 || width <= 0 || lane+width > 16)
            throw new IllegalArgumentException("Note must stay within 16 lanes");
    }
    public NoteDefinition {
        if (id == null || id.isBlank() || kind == null || direction == null)
            throw new IllegalArgumentException("Missing note identity, type or direction");
        checkLane(lane,width);
        if (kind==Kind.SPEED_CHANGE && (lane!=0 || width!=16 || direction!=Direction.NONE
                || (parentId!=null && !parentId.isBlank())))
            throw new IllegalArgumentException("Speed markers span all 16 lanes and have no input or parent");
        if(kind==Kind.AIR_ACTION && width<AIR_ACTION_MIN_BAR_LANES)
            throw new IllegalArgumentException("AIR-ACTION bars must be at least one block wide");
        if (!Double.isFinite(timeMs) || timeMs < 0 || !Double.isFinite(durationMs) || durationMs < 0)
            throw new IllegalArgumentException("Invalid note time");
        nodes = List.copyOf(nodes);
        parentId = parentId == null ? "" : parentId;
        boolean sustained = kind==Kind.HOLD || kind==Kind.SLIDE || kind==Kind.AIR_ACTION;
        if (sustained != (durationMs > 0)) throw new IllegalArgumentException("Invalid duration for note type");
        if (kind==Kind.FLICK && direction!=Direction.ANY && direction!=Direction.LEFT && direction!=Direction.RIGHT)
            throw new IllegalArgumentException("Flick direction must be ANY, LEFT or RIGHT");
        if (kind==Kind.AIR && (direction==Direction.NONE || direction==Direction.ANY
                || direction==Direction.LEFT || direction==Direction.RIGHT))
            throw new IllegalArgumentException("AIR requires a vertical/diagonal direction");
        if (kind==Kind.AIR_ACTION && parentId.isBlank())
            throw new IllegalArgumentException("AIR-ACTION requires an associated AIR id");
        double previous=-1;
        for (Node node:nodes) {
            if(kind==Kind.AIR_ACTION && node.width<AIR_ACTION_MIN_BAR_LANES)
                throw new IllegalArgumentException("AIR-ACTION node bars must be at least one block wide");
            if (node.offsetMs<=previous || node.offsetMs>durationMs) throw new IllegalArgumentException("Unordered path nodes");
            previous=node.offsetMs;
            if (kind==Kind.HOLD && (node.lane!=lane || node.width!=width))
                throw new IllegalArgumentException("HOLD cannot move sideways");
        }
        if (sustained && (nodes.size()<2 || nodes.getFirst().offsetMs!=0 || nodes.getLast().offsetMs!=durationMs))
            throw new IllegalArgumentException("Sustained notes require start/end path nodes");
        if (!sustained && !nodes.isEmpty()) throw new IllegalArgumentException("Instant notes have no path");
        if (sustained && (nodes.getFirst().lane!=lane || nodes.getFirst().width!=width))
            throw new IllegalArgumentException("Start node differs from note position");
    }
    public static NoteDefinition preview(Kind kind,int width) {
        boolean sustained=kind==Kind.HOLD || kind==Kind.SLIDE || kind==Kind.AIR_ACTION;
        double duration=sustained?1000:0;
        return new NoteDefinition("preview_"+kind.name().toLowerCase()+"_"+width,kind,0,0,width,duration,
                kind==Kind.FLICK?Direction.ANY:kind==Kind.AIR?Direction.UP:Direction.NONE,
                kind==Kind.AIR_ACTION?"preview_air_"+width:"",
                sustained?List.of(new Node(0,0,width,kind==Kind.AIR_ACTION?AIR_ACTION_HEIGHT:0),
                        new Node(duration,0,width,kind==Kind.AIR_ACTION?AIR_ACTION_HEIGHT:0)):List.of());
    }
}
