package az.aztu.kanban.dto;

import az.aztu.kanban.domain.ArchNodeKind;
import az.aztu.kanban.domain.ArchNoteKind;
import az.aztu.kanban.domain.ArchNoteStatus;
import az.aztu.kanban.domain.ArchitectureDiagram;
import az.aztu.kanban.domain.ArchitectureEdge;
import az.aztu.kanban.domain.ArchitectureNode;
import az.aztu.kanban.domain.DiagramStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ArchitectureDtos {

    private ArchitectureDtos() {
    }

    // ------------------------------------------------------------------ responses

    public record NodeDto(
            Long id,
            String name,
            ArchNodeKind kind,
            String description,
            String technology,
            String color,
            int x,
            int y,
            int width,
            int height,
            ArchNoteKind noteKind,
            ArchNoteStatus noteStatus,
            LocalDate decidedOn,
            UserDtos.UserSummary author,
            Instant updatedAt
    ) {
        public static NodeDto from(ArchitectureNode node) {
            return new NodeDto(
                    node.getId(),
                    node.getName(),
                    node.getKind(),
                    node.getDescription(),
                    node.getTechnology(),
                    node.getColor(),
                    node.getX(),
                    node.getY(),
                    node.getWidth(),
                    node.getHeight(),
                    node.getNoteKind(),
                    node.getNoteStatus(),
                    node.getDecidedOn(),
                    UserDtos.UserSummary.from(node.getAuthor()),
                    node.getUpdatedAt());
        }
    }

    public record EdgeDto(
            Long id,
            Long sourceNodeId,
            Long targetNodeId,
            String label,
            String technology,
            boolean dashed
    ) {
        public static EdgeDto from(ArchitectureEdge edge) {
            return new EdgeDto(
                    edge.getId(),
                    edge.getSourceNode().getId(),
                    edge.getTargetNode().getId(),
                    edge.getLabel(),
                    edge.getTechnology(),
                    edge.isDashed());
        }
    }

    public record DiagramSummary(
            Long id,
            String name,
            String description,
            String color,
            DiagramStatus status,
            Long platformId,
            String platformName,
            String platformColor,
            UserDtos.UserSummary owner,
            long nodeCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static DiagramSummary from(ArchitectureDiagram diagram, long nodeCount) {
            return new DiagramSummary(
                    diagram.getId(),
                    diagram.getName(),
                    diagram.getDescription(),
                    diagram.getColor(),
                    diagram.getStatus(),
                    diagram.getPlatform() != null ? diagram.getPlatform().getId() : null,
                    diagram.getPlatform() != null ? diagram.getPlatform().getName() : null,
                    diagram.getPlatform() != null ? diagram.getPlatform().getColor() : null,
                    UserDtos.UserSummary.from(diagram.getOwner()),
                    nodeCount,
                    diagram.getCreatedAt(),
                    diagram.getUpdatedAt());
        }
    }

    /** Everything the canvas needs, in one round trip. */
    public record DiagramView(
            DiagramSummary diagram,
            List<NodeDto> nodes,
            List<EdgeDto> edges
    ) {
    }

    // ------------------------------------------------------------------ requests

    public record DiagramRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Size(max = 20) String color,
            DiagramStatus status,
            @NotNull Long platformId,
            Long ownerId
    ) {
    }

    /**
     * Geometry is boxed rather than primitive: Jackson binds a missing JSON field onto a
     * primitive int as 0 without complaint, so a client omitting width would silently persist a
     * zero-width rectangle. Boxed types let @NotNull do its job, and the service unboxes after
     * clamping.
     */
    public record NodeRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull ArchNodeKind kind,
            String description,
            @Size(max = 120) String technology,
            @Size(max = 20) String color,
            @NotNull Integer x,
            @NotNull Integer y,
            Integer width,
            Integer height,
            ArchNoteKind noteKind,
            ArchNoteStatus noteStatus,
            LocalDate decidedOn
    ) {
    }

    public record NodePositionRequest(
            @NotNull Integer x,
            @NotNull Integer y
    ) {
    }

    public record EdgeRequest(
            @NotNull Long sourceNodeId,
            @NotNull Long targetNodeId,
            @Size(max = 80) String label,
            @Size(max = 120) String technology,
            Boolean dashed
    ) {
    }
}
