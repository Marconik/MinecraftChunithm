package io.github.marconik.minecraftchunithm;

/** Each model item exposes its semantic type and a sample attribute definition. */
public final class NoteModelItem extends TapModelItem {
    private final NoteDefinition definition;
    public NoteModelItem(Properties properties,int lanes,NoteDefinition.Kind kind) {
        this(properties,lanes,kind,NoteDefinition.Direction.UP,NoteDefinition.Connection.LINEAR);
    }
    public NoteModelItem(Properties properties,int lanes,NoteDefinition.Kind kind,
                         NoteDefinition.Direction direction,NoteDefinition.Connection connection) {
        super(properties,lanes);
        var base=NoteDefinition.preview(kind,lanes);
        definition=new NoteDefinition(base.id(),kind,base.timeMs(),base.lane(),base.width(),base.durationMs(),
                kind==NoteDefinition.Kind.AIR?direction:base.direction(),base.parentId(),
                base.nodes().stream().map(n->new NoteDefinition.Node(n.offsetMs(),n.lane(),n.width(),
                        kind==NoteDefinition.Kind.AIR_ACTION?1:n.height(),connection)).toList());
    }
    public NoteDefinition definition() { return definition; }
    @Override
    protected double previewFootprintWidth() {
        if(definition.kind()==NoteDefinition.Kind.AIR_ACTION
                && definition.nodes().getFirst().connection()!=NoteDefinition.Connection.LINEAR)return 1.5;
        return super.previewFootprintWidth();
    }
}
