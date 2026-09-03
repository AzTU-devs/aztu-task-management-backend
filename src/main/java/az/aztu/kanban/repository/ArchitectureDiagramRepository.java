package az.aztu.kanban.repository;

import az.aztu.kanban.domain.ArchitectureDiagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchitectureDiagramRepository extends JpaRepository<ArchitectureDiagram, Long> {

    List<ArchitectureDiagram> findAllByOrderByNameAsc();

    List<ArchitectureDiagram> findAllByPlatformIdOrderByNameAsc(Long platformId);

    long countByPlatformId(Long platformId);

    List<ArchitectureDiagram> findAllByOwnerId(Long ownerId);
}
