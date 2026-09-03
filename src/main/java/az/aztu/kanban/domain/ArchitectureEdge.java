package az.aztu.kanban.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A connection between two rectangles. */
@Entity
@Table(name = "arch_edge", indexes = {
        @Index(name = "idx_arch_edge_diagram", columnList = "diagram_id")
})
@Getter
@Setter
public class ArchitectureEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false)
    private ArchitectureDiagram diagram;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_node_id", nullable = false)
    private ArchitectureNode sourceNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_node_id", nullable = false)
    private ArchitectureNode targetNode;

    /** "reads exam results" */
    @Column(length = 80)
    private String label;

    /** "HTTPS/JSON" */
    @Column(length = 120)
    private String technology;

    @Column(nullable = false)
    private boolean dashed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
