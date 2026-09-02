package az.aztu.kanban.repository;

import az.aztu.kanban.domain.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findAllByBoardIdOrderByPositionAsc(Long boardId);

    Optional<BoardColumn> findFirstByBoardIdOrderByPositionAsc(Long boardId);

    long countByBoardId(Long boardId);
}
