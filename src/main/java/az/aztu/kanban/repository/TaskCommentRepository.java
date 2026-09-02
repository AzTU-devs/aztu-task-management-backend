package az.aztu.kanban.repository;

import az.aztu.kanban.domain.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findAllByTaskIdOrderByCreatedAtAsc(Long taskId);

    void deleteAllByTaskId(Long taskId);

    void deleteAllByAuthorId(Long authorId);

    long countByTaskId(Long taskId);

    @Query("SELECT c.task.id, COUNT(c) FROM TaskComment c WHERE c.task.board.id = :boardId GROUP BY c.task.id")
    List<Object[]> countsByBoard(@Param("boardId") Long boardId);
}
