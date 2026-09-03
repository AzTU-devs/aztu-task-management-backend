package az.aztu.kanban.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A rectangle on the canvas. A NOTE-kind node is also the decision record: the note_* columns
 * and the author carry what an ADR would, without a second table to keep in step.
 */
@Entity
@Table(name = "arch_node", indexes = {
        @Index(name = "idx_arch_node_diagram", columnList = "diagram_id")
})
@Getter
@Setter
public class ArchitectureNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false)
    private ArchitectureDiagram diagram;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArchNodeKind kind = ArchNodeKind.COMPONENT;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** "Spring Boot 3.3 / Java 21" - the second line inside the box. */
    @Column(length = 120)
    private String technology;

    /** Null means "use the colour the kind implies". */
    @Column(length = 20)
    private String color;

    @Column(name = "pos_x", nullable = false)
    private int x;

    @Column(name = "pos_y", nullable = false)
    private int y;

    @Column(nullable = false)
    private int width = 220;

    @Column(nullable = false)
    private int height = 100;

    // ---- only meaningful when kind = NOTE ----

    @Enumerated(EnumType.STRING)
    @Column(name = "note_kind", length = 20)
    private ArchNoteKind noteKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_status", length = 20)
    private ArchNoteStatus noteStatus;

    @Column(name = "decided_on")
    private LocalDate decidedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
