package az.aztu.kanban.repository;

import az.aztu.kanban.domain.ArchitectureNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchitectureNodeRepository extends JpaRepository<ArchitectureNode, Long> {

    List<ArchitectureNode> findAllByDiagramIdOrderByIdAsc(Long diagramId);

    long countByDiagramId(Long diagramId);

    void deleteAllByDiagramId(Long diagramId);

    List<ArchitectureNode> findAllByAuthorId(Long authorId);
}
