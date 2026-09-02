package az.aztu.kanban.repository;

import az.aztu.kanban.domain.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findTop50ByTaskIdOrderByCreatedAtDesc(Long taskId);

    Page<Activity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Activity> findAllByBoardIdOrderByCreatedAtDesc(Long boardId, Pageable pageable);

    void deleteAllByTaskId(Long taskId);

    void deleteAllByBoardId(Long boardId);

    List<Activity> findAllByActorId(Long actorId);
}
