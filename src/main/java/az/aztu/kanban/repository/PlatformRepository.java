package az.aztu.kanban.repository;

import az.aztu.kanban.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformRepository extends JpaRepository<Platform, Long> {

    Optional<Platform> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    List<Platform> findAllByOrderByNameAsc();

    List<Platform> findAllByOwnerId(Long ownerId);

    List<Platform> findAllByActiveTrueOrderByNameAsc();
}
