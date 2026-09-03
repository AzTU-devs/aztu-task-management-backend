package az.aztu.kanban.repository;

import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findAllByActiveTrueOrderByFullNameAsc();

    long countByActiveTrue();

    long countByRole(Role role);

    /** {@code searchPattern} must be a {@link SearchTerm#like(String)} pattern, or null. */
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR LOWER(u.fullName) LIKE :search
                                   OR LOWER(u.email) LIKE :search)
              AND (:role IS NULL OR u.role = :role)
              AND (:active IS NULL OR u.active = :active)
            """)
    Page<User> search(@Param("search") String searchPattern,
                      @Param("role") Role role,
                      @Param("active") Boolean active,
                      Pageable pageable);
}
