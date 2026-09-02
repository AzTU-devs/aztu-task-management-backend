package az.aztu.kanban;

import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.AuthDtos.LoginRequest;
import az.aztu.kanban.dto.AuthDtos.LoginResponse;
import az.aztu.kanban.dto.BoardDtos.BoardDetail;
import az.aztu.kanban.dto.BoardDtos.BoardRequest;
import az.aztu.kanban.dto.BoardDtos.KanbanBoard;
import az.aztu.kanban.dto.CommentDtos.CommentRequest;
import az.aztu.kanban.dto.PlatformDtos.PlatformDto;
import az.aztu.kanban.dto.PlatformDtos.PlatformRequest;
import az.aztu.kanban.dto.TaskDtos.CreateTaskRequest;
import az.aztu.kanban.dto.TaskDtos.MoveTaskRequest;
import az.aztu.kanban.dto.TaskDtos.TaskDetail;
import az.aztu.kanban.dto.UserDtos.CreateUserRequest;
import az.aztu.kanban.dto.UserDtos.CreateUserResponse;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import az.aztu.kanban.service.AuthService;
import az.aztu.kanban.service.BoardService;
import az.aztu.kanban.service.CommentService;
import az.aztu.kanban.service.DashboardService;
import az.aztu.kanban.service.NotificationService;
import az.aztu.kanban.service.PlatformService;
import az.aztu.kanban.service.TaskService;
import az.aztu.kanban.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class KanbanApplicationTests {

    @Autowired UserRepository userRepository;
    @Autowired PlatformRepository platformRepository;
    @Autowired BoardRepository boardRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired AuthService authService;
    @Autowired UserService userService;
    @Autowired PlatformService platformService;
    @Autowired BoardService boardService;
    @Autowired TaskService taskService;
    @Autowired CommentService commentService;
    @Autowired DashboardService dashboardService;
    @Autowired NotificationService notificationService;

    private User admin() {
        return userRepository.findByEmailIgnoreCase("admin@aztu.edu.az").orElseThrow();
    }

    @Test
    void seedsAdminAndDemoWorkspace() {
        assertThat(admin().getRole()).isEqualTo(Role.ADMIN);
        assertThat(platformRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(boardRepository.count()).isGreaterThanOrEqualTo(4);
        assertThat(taskRepository.count()).isGreaterThan(0);
    }

    @Test
    void adminCanLogInAndReceiveAToken() {
        LoginResponse response = authService.login(new LoginRequest("admin@aztu.edu.az", "Admin123!"));
        assertThat(response.token()).isNotBlank();
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void fullWorkflowFromPlatformToComment() {
        User admin = admin();

        PlatformDto platform = platformService.create(
                new PlatformRequest("Quality Assurance", "QA", "Testing and audits", "#0ea5e9", "flask", admin.getId(), true));
        assertThat(platform.id()).isNotNull();

        CreateUserResponse member = userService.create(new CreateUserRequest(
                "test.member@aztu.edu.az", "Test Member", Role.MEMBER, "QA Engineer", "QA", null, null));
        assertThat(member.temporaryPassword()).isNotBlank();

        BoardDetail board = boardService.create(new BoardRequest(
                "Audit 2026", "AUD", "Yearly audit board", "#14b8a6",
                platform.id(), member.user().id(), List.of(member.user().id()), null), admin);
        assertThat(board.columns()).hasSize(5);
        assertThat(board.members()).isNotEmpty();

        TaskDetail task = taskService.create(new CreateTaskRequest(
                "Write the audit checklist", "Cover every faculty", board.id(), board.columns().get(0).id(),
                null, null, member.user().id(), LocalDate.now(), LocalDate.now().plusDays(3),
                5, 8.0, java.util.Set.of("audit", "Q1"), List.of(admin.getId())), admin);

        assertThat(task.taskKey()).isEqualTo("AUD-1");
        assertThat(task.assignee().id()).isEqualTo(member.user().id());
        assertThat(task.labels()).contains("audit", "q1");

        // the assignee got an in-app notification
        assertThat(notificationService.unreadCount(member.user().id())).isGreaterThan(0);

        // move it into the "Done" column
        Long doneColumn = board.columns().stream()
                .filter(column -> column.category() == ColumnCategory.DONE)
                .findFirst().orElseThrow().id();
        taskService.move(task.id(), new MoveTaskRequest(doneColumn, 0), admin);

        TaskDetail moved = taskService.getByKey("AUD-1");
        assertThat(moved.category()).isEqualTo(ColumnCategory.DONE);
        assertThat(moved.completedAt()).isNotNull();

        commentService.add(task.id(), new CommentRequest("Checklist finished."), admin);
        assertThat(commentService.list(task.id())).hasSize(1);

        KanbanBoard kanban = boardService.kanban("AUD", null, null, null, null);
        assertThat(kanban.columns()).hasSize(5);
        assertThat(kanban.columns().stream().mapToLong(column -> column.tasks().size()).sum()).isEqualTo(1);

        assertThat(dashboardService.stats(admin).totalBoards()).isGreaterThan(0);
        assertThat(taskService.search(board.id(), null, null, null, null, null, "audit", PageRequest.of(0, 10))
                .totalElements()).isEqualTo(1);
    }

    @Test
    void taskKeysAreSequentialPerBoard() {
        User admin = admin();
        var board = boardRepository.findByBoardKeyIgnoreCase("LMS").orElseThrow();
        TaskDetail first = taskService.create(new CreateTaskRequest(
                "Sequential one", null, board.getId(), null, null, null, null, null, null, null, null, null, null), admin);
        TaskDetail second = taskService.create(new CreateTaskRequest(
                "Sequential two", null, board.getId(), null, null, null, null, null, null, null, null, null, null), admin);

        int firstNumber = Integer.parseInt(first.taskKey().split("-")[1]);
        int secondNumber = Integer.parseInt(second.taskKey().split("-")[1]);
        assertThat(secondNumber).isEqualTo(firstNumber + 1);
    }
}
