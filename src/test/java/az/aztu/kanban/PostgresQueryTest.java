package az.aztu.kanban;

import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.PageResponse;
import az.aztu.kanban.dto.TaskDtos.TaskCard;
import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.dto.BoardDtos;
import az.aztu.kanban.dto.UserDtos.UserSummary;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.TaskType;
import az.aztu.kanban.repository.SearchTerm;
import az.aztu.kanban.repository.UserRepository;
import az.aztu.kanban.service.BoardService;
import az.aztu.kanban.service.DashboardService;
import az.aztu.kanban.service.TaskService;
import az.aztu.kanban.service.UserService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These run against a real PostgreSQL, not H2.
 *
 * H2 happily accepts an untyped null parameter where PostgreSQL raises
 * "function lower(bytea) does not exist", so every optional-filter query has to be
 * proven against the real database or the failure only shows up in production.
 */
@SpringBootTest
class PostgresQueryTest {

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the embedded PostgreSQL", ex);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("app.jwt.secret", () -> "postgres-test-secret-key-long-enough-for-hmac-sha256-0123456789");
        registry.add("app.mail.enabled", () -> "false");
        registry.add("app.seed.demo-data", () -> "true");
    }

    @Autowired UserRepository userRepository;
    @Autowired TaskService taskService;
    @Autowired BoardService boardService;
    @Autowired DashboardService dashboardService;
    @Autowired UserService userService;
    @Autowired PlatformRepository platformRepository;

    private User admin() {
        return userRepository.findByEmailIgnoreCase("admin@aztu.edu.az").orElseThrow();
    }

    /** This is the exact call the dashboard makes, and the one that failed on the server. */
    @Test
    void dashboardStatsWorkWithNoSearchTerm() {
        var stats = dashboardService.stats(admin());
        assertThat(stats.totalBoards()).isPositive();
        assertThat(stats.myTasks()).isNotNull();
    }

    @Test
    void taskSearchWorksWithEveryFilterNull() {
        PageResponse<TaskCard> page = taskService.search(null, null, null, null, null, null, null,
                PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "updatedAt")));
        assertThat(page.content()).isNotEmpty();
    }

    @Test
    void taskSearchWorksWithAnActualSearchTerm() {
        PageResponse<TaskCard> page = taskService.search(null, null, null, null, null, null, "requirements",
                PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "updatedAt")));
        assertThat(page.content()).isNotEmpty();
        assertThat(page.content().getFirst().title().toLowerCase()).contains("requirements");
    }

    @Test
    void userSearchWorksWithEveryFilterNull() {
        var page = userRepository.search(null, null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    void userSearchWorksWithATerm() {
        var page = userRepository.search(SearchTerm.like("nigar"), null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
    }

    /** The path the API actually takes, so the wrapping is covered too. */
    @Test
    void userSearchThroughTheServiceFindsPeopleByNameAndEmail() {
        assertThat(userService.search("NIGAR", null, null, PageRequest.of(0, 20)).content()).hasSize(1);
        assertThat(userService.search("aztu.edu.az", null, null, PageRequest.of(0, 20)).content()).isNotEmpty();
        assertThat(userService.search("  ", null, null, PageRequest.of(0, 20)).content()).isNotEmpty();
        assertThat(userService.search("nobody-here", null, null, PageRequest.of(0, 20)).content()).isEmpty();
    }

    /** Every optional filter set at once, on PostgreSQL. */
    @Test
    void taskSearchWorksWithEveryFilterPopulated() {
        var page = taskService.search(null, null, null, TaskType.TASK, Priority.MEDIUM,
                ColumnCategory.TODO, "design", PageRequest.of(0, 25, Sort.by("updatedAt")));
        assertThat(page).isNotNull();
    }

    /**
     * The creator arrives from the JWT filter, loaded in its own transaction, so it is a
     * DETACHED User. The lead and the members are loaded inside this transaction, so they
     * are MANAGED. Without equals/hashCode on User those are different objects for the
     * same person, and board_member gets two rows for one (board_id, user_id).
     */
    @Test
    void creatingABoardWhereTheCreatorIsAlsoLeadAndMemberDoesNotDuplicateMembership() {
        User creator = admin();          // detached, exactly like the request principal
        Long platformId = platformRepository.findAll().getFirst().getId();

        var request = new BoardDtos.BoardRequest(
                "Duplicate Membership Check", "DUPX", "created by someone who is also the lead",
                null, platformId, creator.getId(), List.of(creator.getId()), null);

        var board = boardService.create(request, creator);

        assertThat(board.members()).extracting(UserSummary::id).containsExactly(creator.getId());
        assertThat(board.members()).hasSize(1);
    }

    @Test
    void kanbanWorksWithAndWithoutASearchTerm() {
        var board = boardService.list(null, null).getFirst();
        assertThat(boardService.kanban(board.boardKey(), null, null, null, null).columns()).isNotEmpty();
        assertThat(boardService.kanban(board.boardKey(), null, null, null, "design")).isNotNull();
    }
}
