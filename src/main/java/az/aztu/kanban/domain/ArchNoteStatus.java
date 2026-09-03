package az.aztu.kanban.domain;

/** Only meaningful on a NOTE node: where the decision stands. */
public enum ArchNoteStatus {
    PROPOSED,
    ACCEPTED,
    SUPERSEDED,
    REJECTED
}
