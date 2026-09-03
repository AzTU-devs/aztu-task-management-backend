package az.aztu.kanban.service;

import az.aztu.kanban.domain.ArchNodeKind;
import az.aztu.kanban.domain.ArchitectureDiagram;
import az.aztu.kanban.domain.ArchitectureEdge;
import az.aztu.kanban.domain.ArchitectureNode;
import az.aztu.kanban.domain.DiagramStatus;
import az.aztu.kanban.domain.Platform;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.ArchitectureDtos.DiagramRequest;
import az.aztu.kanban.dto.ArchitectureDtos.DiagramSummary;
import az.aztu.kanban.dto.ArchitectureDtos.DiagramView;
import az.aztu.kanban.dto.ArchitectureDtos.EdgeDto;
import az.aztu.kanban.dto.ArchitectureDtos.EdgeRequest;
import az.aztu.kanban.dto.ArchitectureDtos.NodeDto;
import az.aztu.kanban.dto.ArchitectureDtos.NodePositionRequest;
import az.aztu.kanban.dto.ArchitectureDtos.NodeRequest;
import az.aztu.kanban.exception.BadRequestException;
import az.aztu.kanban.exception.ConflictException;
import az.aztu.kanban.exception.NotFoundException;
import az.aztu.kanban.repository.ArchitectureDiagramRepository;
import az.aztu.kanban.repository.ArchitectureEdgeRepository;
import az.aztu.kanban.repository.ArchitectureNodeRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArchitectureService {

    private final ArchitectureDiagramRepository diagramRepository;
    private final ArchitectureNodeRepository nodeRepository;
    private final ArchitectureEdgeRepository edgeRepository;
    private final PlatformRepository platformRepository;
    private final UserRepository userRepository;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<DiagramSummary> list(Long platformId) {
        // Branching in Java rather than passing a nullable parameter into a ":x IS NULL OR ..."
        // clause. That pattern is what produced "function lower(bytea) does not exist" on
        // PostgreSQL twice already, and it is avoidable here.
        List<ArchitectureDiagram> diagrams = platformId == null
                ? diagramRepository.findAllByOrderByNameAsc()
                : diagramRepository.findAllByPlatformIdOrderByNameAsc(platformId);
        return diagrams.stream()
                .map(diagram -> DiagramSummary.from(diagram, nodeRepository.countByDiagramId(diagram.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DiagramView view(Long diagramId) {
        ArchitectureDiagram diagram = getDiagram(diagramId);
        List<NodeDto> nodes = nodeRepository.findAllByDiagramIdOrderByIdAsc(diagramId).stream()
                .map(NodeDto::from)
                .toList();
        List<EdgeDto> edges = edgeRepository.findAllByDiagramIdOrderByIdAsc(diagramId).stream()
                .map(EdgeDto::from)
                .toList();
        return new DiagramView(DiagramSummary.from(diagram, nodes.size()), nodes, edges);
    }

    @Transactional(readOnly = true)
    public ArchitectureDiagram getDiagram(Long id) {
        return diagramRepository.findById(id).orElseThrow(() -> NotFoundException.of("Diagram", id));
    }

    // ---------------------------------------------------------------- diagram

    @Transactional
    public DiagramSummary create(DiagramRequest request, User creator) {
        ArchitectureDiagram diagram = new ArchitectureDiagram();
        apply(diagram, request, creator);
        diagramRepository.save(diagram);
        return DiagramSummary.from(diagram, 0);
    }

    @Transactional
    public DiagramSummary update(Long id, DiagramRequest request) {
        ArchitectureDiagram diagram = getDiagram(id);
        apply(diagram, request, null);
        diagramRepository.save(diagram);
        return DiagramSummary.from(diagram, nodeRepository.countByDiagramId(id));
    }

    @Transactional
    public void delete(Long id) {
        ArchitectureDiagram diagram = getDiagram(id);
        // Edges reference nodes, so they go first. The flush forces the DELETEs to reach the
        // database in this order rather than in whatever order Hibernate's action queue chooses.
        edgeRepository.deleteAllByDiagramId(id);
        edgeRepository.flush();
        nodeRepository.deleteAllByDiagramId(id);
        nodeRepository.flush();
        diagramRepository.delete(diagram);
    }

    private void apply(ArchitectureDiagram diagram, DiagramRequest request, User creator) {
        Platform platform = platformRepository.findById(request.platformId())
                .orElseThrow(() -> NotFoundException.of("Platform", request.platformId()));
        diagram.setName(request.name().trim());
        diagram.setDescription(request.description());
        diagram.setPlatform(platform);
        if (request.color() != null && !request.color().isBlank()) {
            diagram.setColor(request.color());
        }
        if (request.status() != null) {
            diagram.setStatus(request.status());
        }
        if (request.ownerId() != null) {
            diagram.setOwner(userRepository.findById(request.ownerId())
                    .orElseThrow(() -> NotFoundException.of("User", request.ownerId())));
        } else if (creator != null) {
            diagram.setOwner(userRepository.findById(creator.getId()).orElse(null));
        } else {
            diagram.setOwner(null);
        }
        if (diagram.getStatus() == null) {
            diagram.setStatus(DiagramStatus.DRAFT);
        }
    }

    // ---------------------------------------------------------------- nodes

    @Transactional
    public NodeDto addNode(Long diagramId, NodeRequest request, User creator) {
        ArchitectureDiagram diagram = getDiagram(diagramId);
        ArchitectureNode node = new ArchitectureNode();
        node.setDiagram(diagram);
        applyNode(node, request);
        if (request.kind() == ArchNodeKind.NOTE && creator != null) {
            node.setAuthor(userRepository.findById(creator.getId()).orElse(null));
        }
        nodeRepository.save(node);
        touch(diagram);
        return NodeDto.from(node);
    }

    @Transactional
    public NodeDto updateNode(Long nodeId, NodeRequest request) {
        ArchitectureNode node = getNode(nodeId);
        applyNode(node, request);
        nodeRepository.save(node);
        touch(node.getDiagram());
        return NodeDto.from(node);
    }

    /**
     * The drag path: one node, two integers, one row. Deliberately narrow so a drop can never
     * overwrite a label somebody edited while the pointer was down.
     */
    @Transactional
    public NodeDto moveNode(Long nodeId, NodePositionRequest request) {
        ArchitectureNode node = getNode(nodeId);
        // Clamped against this node's persisted size, so the server's answer matches what the
        // client already drew rather than snapping the rectangle somewhere else on arrival.
        node.setX(ArchitectureGeometry.clampX(request.x(), node.getWidth()));
        node.setY(ArchitectureGeometry.clampY(request.y(), node.getHeight()));
        nodeRepository.save(node);
        touch(node.getDiagram());
        return NodeDto.from(node);
    }

    @Transactional
    public void deleteNode(Long nodeId) {
        ArchitectureNode node = getNode(nodeId);
        ArchitectureDiagram diagram = node.getDiagram();
        // No connection may outlive the box it points at.
        edgeRepository.deleteAllBySourceNodeIdOrTargetNodeId(nodeId, nodeId);
        edgeRepository.flush();
        nodeRepository.delete(node);
        nodeRepository.flush();
        touch(diagram);
    }

    private void applyNode(ArchitectureNode node, NodeRequest request) {
        int width = ArchitectureGeometry.clampWidth(request.width());
        int height = ArchitectureGeometry.clampHeight(request.height());
        node.setName(request.name().trim());
        node.setKind(request.kind());
        node.setDescription(request.description());
        node.setTechnology(blankToNull(request.technology()));
        node.setColor(blankToNull(request.color()));
        node.setWidth(width);
        node.setHeight(height);
        node.setX(ArchitectureGeometry.clampX(request.x(), width));
        node.setY(ArchitectureGeometry.clampY(request.y(), height));
        node.setNoteKind(request.kind() == ArchNodeKind.NOTE ? request.noteKind() : null);
        node.setNoteStatus(request.kind() == ArchNodeKind.NOTE ? request.noteStatus() : null);
        node.setDecidedOn(request.kind() == ArchNodeKind.NOTE ? request.decidedOn() : null);
    }

    private ArchitectureNode getNode(Long id) {
        return nodeRepository.findById(id).orElseThrow(() -> NotFoundException.of("Node", id));
    }

    // ---------------------------------------------------------------- edges

    @Transactional
    public EdgeDto addEdge(Long diagramId, EdgeRequest request) {
        ArchitectureDiagram diagram = getDiagram(diagramId);
        if (request.sourceNodeId().equals(request.targetNodeId())) {
            throw new BadRequestException("A component cannot be connected to itself.");
        }
        ArchitectureNode source = requireNodeOfDiagram(request.sourceNodeId(), diagramId);
        ArchitectureNode target = requireNodeOfDiagram(request.targetNodeId(), diagramId);
        if (edgeRepository.existsBySourceNodeIdAndTargetNodeId(source.getId(), target.getId())) {
            throw new ConflictException("Those two components are already connected.");
        }

        ArchitectureEdge edge = new ArchitectureEdge();
        edge.setDiagram(diagram);
        edge.setSourceNode(source);
        edge.setTargetNode(target);
        edge.setLabel(blankToNull(request.label()));
        edge.setTechnology(blankToNull(request.technology()));
        edge.setDashed(Boolean.TRUE.equals(request.dashed()));
        edgeRepository.save(edge);
        touch(diagram);
        return EdgeDto.from(edge);
    }

    /** Only the label, technology and styling can change - re-pointing an edge is a delete plus an add. */
    @Transactional
    public EdgeDto updateEdge(Long edgeId, EdgeRequest request) {
        ArchitectureEdge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> NotFoundException.of("Connection", edgeId));
        edge.setLabel(blankToNull(request.label()));
        edge.setTechnology(blankToNull(request.technology()));
        edge.setDashed(Boolean.TRUE.equals(request.dashed()));
        edgeRepository.save(edge);
        touch(edge.getDiagram());
        return EdgeDto.from(edge);
    }

    @Transactional
    public void deleteEdge(Long edgeId) {
        ArchitectureEdge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> NotFoundException.of("Connection", edgeId));
        ArchitectureDiagram diagram = edge.getDiagram();
        edgeRepository.delete(edge);
        touch(diagram);
    }

    private ArchitectureNode requireNodeOfDiagram(Long nodeId, Long diagramId) {
        ArchitectureNode node = getNode(nodeId);
        if (!node.getDiagram().getId().equals(diagramId)) {
            throw new BadRequestException("That component belongs to a different diagram.");
        }
        return node;
    }

    // ---------------------------------------------------------------- helpers

    private void touch(ArchitectureDiagram diagram) {
        diagram.setUpdatedAt(java.time.Instant.now());
        diagramRepository.save(diagram);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
