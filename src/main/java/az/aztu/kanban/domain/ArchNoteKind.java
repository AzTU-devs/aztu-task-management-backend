package az.aztu.kanban.domain;

/** Only meaningful on a NOTE node: what kind of record it is. */
public enum ArchNoteKind {
    DECISION,
    CONSTRAINT,
    RISK,
    ASSUMPTION,
    NOTE
}
