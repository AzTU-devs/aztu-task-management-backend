package az.aztu.kanban.repository;

import az.aztu.kanban.domain.ArchitectureEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchitectureEdgeRepository extends JpaRepository<ArchitectureEdge, Long> {

    List<ArchitectureEdge> findAllByDiagramIdOrderByIdAsc(Long diagramId);

    void deleteAllByDiagramId(Long diagramId);

    /** Every edge touching a node, so deleting a node can never leave a dangling connection. */
    void deleteAllBySourceNodeIdOrTargetNodeId(Long sourceNodeId, Long targetNodeId);

    boolean existsBySourceNodeIdAndTargetNodeId(Long sourceNodeId, Long targetNodeId);
}
