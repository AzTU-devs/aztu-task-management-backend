package az.aztu.kanban.repository;

import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.TaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByTaskKeyIgnoreCase(String taskKey);

    @Query("""
            SELECT t FROM Task t
            LEFT JOIN FETCH t.assignee
            LEFT JOIN FETCH t.reporter
            JOIN FETCH t.boardColumn
            WHERE t.board.id = :boardId
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND (:type IS NULL OR t.type = :type)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:search IS NULL OR LOWER(t.title) LIKE :search
                                   OR LOWER(t.taskKey) LIKE :search)
            ORDER BY t.orderIndex ASC
            """)
    List<Task> findForBoard(@Param("boardId") Long boardId,
                            @Param("assigneeId") Long assigneeId,
                            @Param("type") TaskType type,
                            @Param("priority") Priority priority,
                            @Param("search") String searchPattern);

    List<Task> findAllByBoardColumnIdOrderByOrderIndexAsc(Long columnId);

    List<Task> findAllByBoardId(Long boardId);

    List<Task> findAllByReporterId(Long reporterId);

    @Query("SELECT t FROM Task t JOIN t.watchers w WHERE w.id = :userId")
    List<Task> findAllByWatcherId(@Param("userId") Long userId);

    long countByBoardId(Long boardId);

    long countByBoardColumnId(Long columnId);

    long countByAssigneeId(Long assigneeId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.boardColumn.category = :category")
    long countByCategory(@Param("category") ColumnCategory category);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignee.id = :userId AND t.boardColumn.category <> az.aztu.kanban.domain.ColumnCategory.DONE")
    long countOpenForAssignee(@Param("userId") Long userId);

    @Query("""
            SELECT t FROM Task t
            WHERE (:boardId IS NULL OR t.board.id = :boardId)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND (:reporterId IS NULL OR t.reporter.id = :reporterId)
              AND (:type IS NULL OR t.type = :type)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:category IS NULL OR t.boardColumn.category = :category)
              AND (:search IS NULL OR LOWER(t.title) LIKE :search
                                   OR LOWER(t.taskKey) LIKE :search)
            """)
    Page<Task> search(@Param("boardId") Long boardId,
                      @Param("assigneeId") Long assigneeId,
                      @Param("reporterId") Long reporterId,
                      @Param("type") TaskType type,
                      @Param("priority") Priority priority,
                      @Param("category") ColumnCategory category,
                      @Param("search") String searchPattern,
                      Pageable pageable);

    @Query("""
            SELECT t FROM Task t
            WHERE t.dueDate IS NOT NULL
              AND t.dueDate <= :date
              AND t.boardColumn.category <> az.aztu.kanban.domain.ColumnCategory.DONE
            """)
    List<Task> findDueOnOrBefore(@Param("date") LocalDate date);

    @Query("""
            SELECT t FROM Task t
            WHERE t.dueDate = :date
              AND t.assignee IS NOT NULL
              AND t.boardColumn.category <> az.aztu.kanban.domain.ColumnCategory.DONE
            """)
    List<Task> findDueOn(@Param("date") LocalDate date);

    @Query("SELECT t.priority, COUNT(t) FROM Task t GROUP BY t.priority")
    List<Object[]> countGroupedByPriority();

    @Query("SELECT t.type, COUNT(t) FROM Task t GROUP BY t.type")
    List<Object[]> countGroupedByType();

    @Query("SELECT t.boardColumn.category, COUNT(t) FROM Task t GROUP BY t.boardColumn.category")
    List<Object[]> countGroupedByCategory();
}
