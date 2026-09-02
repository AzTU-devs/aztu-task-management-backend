package az.aztu.kanban.repository;

import az.aztu.kanban.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    Optional<Board> findByBoardKeyIgnoreCase(String boardKey);

    boolean existsByBoardKeyIgnoreCase(String boardKey);

    List<Board> findAllByOrderByNameAsc();

    List<Board> findAllByPlatformIdOrderByNameAsc(Long platformId);

    long countByPlatformId(Long platformId);

    @Query("""
            SELECT DISTINCT b FROM Board b
            LEFT JOIN b.members m
            WHERE b.archived = false AND (m.id = :userId OR b.lead.id = :userId)
            ORDER BY b.name ASC
            """)
    List<Board> findBoardsForUser(@Param("userId") Long userId);

    @Query("SELECT b FROM Board b JOIN b.members m WHERE m.id = :userId")
    List<Board> findAllByMemberId(@Param("userId") Long userId);

    List<Board> findAllByLeadId(Long leadId);

    @Query("SELECT COUNT(b) > 0 FROM Board b LEFT JOIN b.members m WHERE b.id = :boardId AND (m.id = :userId OR b.lead.id = :userId)")
    boolean isMember(@Param("boardId") Long boardId, @Param("userId") Long userId);
}
