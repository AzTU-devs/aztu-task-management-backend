package az.aztu.kanban;

import az.aztu.kanban.domain.ArchNodeKind;
import az.aztu.kanban.domain.ArchNoteKind;
import az.aztu.kanban.domain.ArchNoteStatus;
import az.aztu.kanban.domain.DiagramStatus;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.ArchitectureDtos.DiagramRequest;
import az.aztu.kanban.dto.ArchitectureDtos.DiagramSummary;
import az.aztu.kanban.dto.ArchitectureDtos.EdgeRequest;
import az.aztu.kanban.dto.ArchitectureDtos.NodeDto;
import az.aztu.kanban.dto.ArchitectureDtos.NodePositionRequest;
import az.aztu.kanban.dto.ArchitectureDtos.NodeRequest;
import az.aztu.kanban.exception.BadRequestException;
import az.aztu.kanban.exception.ConflictException;
import az.aztu.kanban.repository.ArchitectureEdgeRepository;
import az.aztu.kanban.repository.ArchitectureNodeRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.UserRepository;
import az.aztu.kanban.service.ArchitectureService;
import az.aztu.kanban.service.PlatformService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The architecture feature against real PostgreSQL. Every case here is one the design review
 * flagged as able to fail on PostgreSQL while passing on H2 - foreign-key ordering on delete,
 * and the geometry contract the canvas depends on.
 */
@SpringBootTest
class ArchitecturePostgresTest {

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the embedded PostgreSQL", ex);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("app.jwt.secret", () -> "architecture-test-secret-key-long-enough-for-hmac-0123456789");
        registry.add("app.mail.enabled", () -> "false");
        registry.add("app.seed.demo-data", () -> "true");
    }

    @Autowired ArchitectureService architecture;
    @Autowired PlatformService platformService;
    @Autowired PlatformRepository platformRepository;
    @Autowired UserRepository userRepository;
    @Autowired ArchitectureNodeRepository nodeRepository;
    @Autowired ArchitectureEdgeRepository edgeRepository;

    private User admin() {
        return userRepository.findByEmailIgnoreCase("admin@aztu.edu.az").orElseThrow();
    }

    private DiagramSummary newDiagram(String name) {
        Long platformId = platformRepository.findAll().getFirst().getId();
        return architecture.create(
                new DiagramRequest(name, "for tests", null, DiagramStatus.DRAFT, platformId, null), admin());
    }

    private NodeDto node(Long diagramId, String name, int x, int y) {
        return architecture.addNode(diagramId,
                new NodeRequest(name, ArchNodeKind.SERVICE, null, null, null, x, y, 220, 100, null, null, null),
                admin());
    }

    @Test
    void aDiagramRoundTripsWithItsNodesAndEdges() {
        var diagram = newDiagram("Round trip");
        var a = node(diagram.id(), "Portal", 100, 100);
        var b = node(diagram.id(), "Exam service", 600, 100);
        architecture.addEdge(diagram.id(), new EdgeRequest(a.id(), b.id(), "reads results", "HTTPS/JSON", false));

        var view = architecture.view(diagram.id());
        assertThat(view.nodes()).hasSize(2);
        assertThat(view.edges()).hasSize(1);
        assertThat(view.edges().getFirst().label()).isEqualTo("reads results");
        assertThat(view.edges().getFirst().technology()).isEqualTo("HTTPS/JSON");
    }

    /** The canvas draws the snapped position immediately; the server must agree exactly. */
    @Test
    void positionsAreSnappedAndClampedAgainstTheNodesOwnSize() {
        var diagram = newDiagram("Geometry");
        var n = node(diagram.id(), "Box", 0, 0);

        assertThat(architecture.moveNode(n.id(), new NodePositionRequest(137, 144)).x()).isEqualTo(140);
        assertThat(architecture.moveNode(n.id(), new NodePositionRequest(137, 144)).y()).isEqualTo(140);

        // 220 wide in a 4000 world, so the furthest left edge is 3780
        var far = architecture.moveNode(n.id(), new NodePositionRequest(3990, 9999));
        assertThat(far.x()).isEqualTo(4000 - 220);
        assertThat(far.y()).isEqualTo(2600 - 100);

        var negative = architecture.moveNode(n.id(), new NodePositionRequest(-500, -500));
        assertThat(negative.x()).isZero();
        assertThat(negative.y()).isZero();
    }

    /** Deleting a box must take its connections with it, or the FK aborts the transaction. */
    @Test
    void deletingANodeRemovesEveryEdgeTouchingIt() {
        var diagram = newDiagram("Node delete");
        var a = node(diagram.id(), "A", 100, 100);
        var b = node(diagram.id(), "B", 600, 100);
        var c = node(diagram.id(), "C", 1100, 100);
        architecture.addEdge(diagram.id(), new EdgeRequest(a.id(), b.id(), null, null, false));
        architecture.addEdge(diagram.id(), new EdgeRequest(c.id(), b.id(), null, null, false));
        assertThat(edgeRepository.findAllByDiagramIdOrderByIdAsc(diagram.id())).hasSize(2);

        architecture.deleteNode(b.id());

        assertThat(edgeRepository.findAllByDiagramIdOrderByIdAsc(diagram.id())).isEmpty();
        assertThat(nodeRepository.findAllByDiagramIdOrderByIdAsc(diagram.id())).hasSize(2);
    }

    @Test
    void deletingADiagramRemovesItsEdgesThenItsNodes() {
        var diagram = newDiagram("Diagram delete");
        var a = node(diagram.id(), "A", 100, 100);
        var b = node(diagram.id(), "B", 600, 100);
        architecture.addEdge(diagram.id(), new EdgeRequest(a.id(), b.id(), null, null, false));

        architecture.delete(diagram.id());

        assertThat(nodeRepository.findAllByDiagramIdOrderByIdAsc(diagram.id())).isEmpty();
        assertThat(edgeRepository.findAllByDiagramIdOrderByIdAsc(diagram.id())).isEmpty();
    }

    @Test
    void aPlatformHoldingADiagramCannotBeDeleted() {
        var platform = platformService.create(new az.aztu.kanban.dto.PlatformDtos.PlatformRequest(
                "Architecture only", "ARCHX", null, null, null, null, true));
        architecture.create(new DiagramRequest("Held", null, null, null, platform.id(), null), admin());

        assertThatThrownBy(() -> platformService.delete(platform.id()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("architecture diagram");
    }

    @Test
    void connectionsAreValidated() {
        var diagram = newDiagram("Edges");
        var other = newDiagram("Elsewhere");
        var a = node(diagram.id(), "A", 100, 100);
        var b = node(diagram.id(), "B", 600, 100);
        var stranger = node(other.id(), "Stranger", 100, 100);

        assertThatThrownBy(() -> architecture.addEdge(diagram.id(), new EdgeRequest(a.id(), a.id(), null, null, false)))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> architecture.addEdge(diagram.id(),
                new EdgeRequest(a.id(), stranger.id(), null, null, false)))
                .isInstanceOf(BadRequestException.class);

        architecture.addEdge(diagram.id(), new EdgeRequest(a.id(), b.id(), null, null, false));
        assertThatThrownBy(() -> architecture.addEdge(diagram.id(), new EdgeRequest(a.id(), b.id(), null, null, false)))
                .isInstanceOf(ConflictException.class);
    }

    /** A NOTE carries the decision record; changing kind away from NOTE clears those fields. */
    @Test
    void noteFieldsAreKeptOnlyForNoteNodes() {
        var diagram = newDiagram("Notes");
        var note = architecture.addNode(diagram.id(),
                new NodeRequest("Use PostgreSQL", ArchNodeKind.NOTE, "Chosen for JSONB support.",
                        null, null, 100, 100, 260, 140,
                        ArchNoteKind.DECISION, ArchNoteStatus.ACCEPTED, java.time.LocalDate.of(2026, 2, 1)),
                admin());

        assertThat(note.noteKind()).isEqualTo(ArchNoteKind.DECISION);
        assertThat(note.noteStatus()).isEqualTo(ArchNoteStatus.ACCEPTED);
        assertThat(note.author()).isNotNull();
        assertThat(note.author().email()).isEqualTo("admin@aztu.edu.az");

        var promoted = architecture.updateNode(note.id(),
                new NodeRequest("Now a service", ArchNodeKind.SERVICE, null, null, null, 100, 100, 220, 100,
                        ArchNoteKind.DECISION, ArchNoteStatus.ACCEPTED, java.time.LocalDate.now()));
        assertThat(promoted.noteKind()).isNull();
        assertThat(promoted.noteStatus()).isNull();
        assertThat(promoted.decidedOn()).isNull();
    }

    @Test
    void listingFiltersByPlatformWithoutANullableQueryParameter() {
        var all = architecture.list(null);
        Long platformId = platformRepository.findAll().getFirst().getId();
        var forPlatform = architecture.list(platformId);
        assertThat(all).isNotNull();
        assertThat(forPlatform).allMatch(d -> platformId.equals(d.platformId()));
    }
}
