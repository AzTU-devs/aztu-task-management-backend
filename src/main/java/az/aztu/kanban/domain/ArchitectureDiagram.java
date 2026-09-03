package az.aztu.kanban.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One architecture model: a canvas of rectangles and the connections between them.
 *
 * There is deliberately no cascading collection of nodes or edges here. The service deletes
 * children explicitly in foreign-key order, and a cascade would fight that ordering - Hibernate
 * orders cascaded child deletes by property declaration, which would try to remove nodes before
 * the edges that point at them.
 */
@Entity
@Table(name = "arch_diagram")
@Getter
@Setter
public class ArchitectureDiagram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 20)
    private String color = "#6366f1";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagramStatus status = DiagramStatus.DRAFT;

    /**
     * Nullable at the database level on purpose: ddl-auto=update can never relax a NOT NULL
     * column later without destructive DDL. "A diagram must belong to a platform" is enforced
     * with @NotNull on the request record instead, which is the same rule without the trap.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id")
    private Platform platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

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
