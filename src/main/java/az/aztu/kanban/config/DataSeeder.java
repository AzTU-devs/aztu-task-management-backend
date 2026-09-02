package az.aztu.kanban.config;

import az.aztu.kanban.domain.Board;
import az.aztu.kanban.domain.BoardColumn;
import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.Platform;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.TaskType;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the bootstrap administrator on first start and, optionally, a small
 * demo workspace so the board is not empty on day one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PlatformRepository platformRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.full-name}")
    private String adminFullName;

    @Value("${app.seed.demo-data}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) {
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setEmail(adminEmail.toLowerCase());
            admin.setFullName(adminFullName);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setTitle("System Administrator");
            admin.setDepartment("IT Department");
            admin.setAvatarColor("#6366f1");
            admin.setActive(true);
            userRepository.save(admin);
            log.info("Bootstrap administrator created: {}", adminEmail);
        }

        if (!seedDemoData || platformRepository.count() > 0) {
            return;
        }

        log.info("Seeding demo workspace...");

        User rector = createUser("nigar.aliyeva@aztu.edu.az", "Nigar Aliyeva", Role.MANAGER,
                "Head of Digital Transformation", "Rectorate", "#0ea5e9");
        User lecturer = createUser("elvin.mammadov@aztu.edu.az", "Elvin Mammadov", Role.MEMBER,
                "Senior Lecturer", "Computer Engineering", "#14b8a6");
        User engineer = createUser("leyla.huseynova@aztu.edu.az", "Leyla Huseynova", Role.MEMBER,
                "Systems Engineer", "IT Department", "#f59e0b");

        Platform education = createPlatform("Education Platform", "EDU",
                "Learning management, curricula and everything students touch.", "#6366f1", "graduation-cap", admin);
        Platform research = createPlatform("Research & Innovation", "RND",
                "Grants, laboratories and scientific publication pipelines.", "#14b8a6", "flask", rector);
        Platform campus = createPlatform("Campus IT", "OPS",
                "Network, servers and internal services of the university campus.", "#f59e0b", "server", engineer);

        Set<User> everyone = new LinkedHashSet<>(List.of(admin, rector, lecturer, engineer));

        Board lms = createBoard("Learning Management System", "LMS", education, rector, everyone,
                "Modernisation of the AzTU e-learning portal.", "#6366f1");
        Board portal = createBoard("Student Portal", "SP", education, lecturer, everyone,
                "Self-service portal for students: grades, schedule, documents.", "#8b5cf6");
        Board grants = createBoard("Grant Programmes", "GRA", research, rector, everyone,
                "Tracking of national and international research grants.", "#14b8a6");
        Board infra = createBoard("Infrastructure", "INF", campus, engineer, everyone,
                "Campus network, data centre and internal IT services.", "#f59e0b");

        seedTasks(lms, admin, rector, lecturer);
        seedTasks(portal, admin, lecturer, rector);
        seedTasks(grants, rector, rector, lecturer);
        seedTasks(infra, engineer, engineer, admin);

        log.info("Demo workspace ready: {} platforms, {} boards, {} tasks",
                platformRepository.count(), boardRepository.count(), taskRepository.count());
    }

    private User createUser(String email, String fullName, Role role, String title, String department, String color) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setRole(role);
            user.setTitle(title);
            user.setDepartment(department);
            user.setAvatarColor(color);
            user.setPasswordHash(passwordEncoder.encode("Aztu2024!"));
            user.setMustChangePassword(true);
            user.setActive(true);
            return userRepository.save(user);
        });
    }

    private Platform createPlatform(String name, String code, String description, String color,
                                    String icon, User owner) {
        Platform platform = new Platform();
        platform.setName(name);
        platform.setCode(code);
        platform.setDescription(description);
        platform.setColor(color);
        platform.setIcon(icon);
        platform.setOwner(owner);
        platform.setActive(true);
        return platformRepository.save(platform);
    }

    private Board createBoard(String name, String key, Platform platform, User lead, Set<User> members,
                              String description, String color) {
        Board board = new Board();
        board.setName(name);
        board.setBoardKey(key);
        board.setPlatform(platform);
        board.setLead(lead);
        board.setDescription(description);
        board.setColor(color);
        board.setMembers(new LinkedHashSet<>(members));

        String[][] columns = {
                {"Backlog", "TODO", "0"},
                {"To Do", "TODO", "0"},
                {"In Progress", "IN_PROGRESS", "5"},
                {"In Review", "IN_PROGRESS", "3"},
                {"Done", "DONE", "0"}
        };
        int position = 0;
        for (String[] definition : columns) {
            BoardColumn column = new BoardColumn();
            column.setName(definition[0]);
            column.setCategory(ColumnCategory.valueOf(definition[1]));
            int wip = Integer.parseInt(definition[2]);
            column.setWipLimit(wip == 0 ? null : wip);
            column.setPosition(position++);
            column.setBoard(board);
            board.getColumns().add(column);
        }
        return boardRepository.save(board);
    }

    private void seedTasks(Board board, User reporter, User assignee, User watcher) {
        String[][] definitions = {
                {"Define functional requirements", "STORY", "HIGH", "0", "3"},
                {"Design the information architecture", "TASK", "MEDIUM", "0", "7"},
                {"Prepare the database schema", "TASK", "HIGH", "1", "5"},
                {"Single sign-on with university accounts", "STORY", "HIGHEST", "2", "10"},
                {"Fix the broken PDF export", "BUG", "HIGH", "2", "2"},
                {"Accessibility review (WCAG 2.1)", "IMPROVEMENT", "LOW", "3", "14"},
                {"Publish the first release notes", "TASK", "MEDIUM", "4", "-2"}
        };

        List<BoardColumn> columns = board.getColumns();
        int counter = board.getTaskCounter();
        for (String[] definition : definitions) {
            BoardColumn column = columns.get(Integer.parseInt(definition[3]));
            Task task = new Task();
            task.setTaskKey(board.getBoardKey() + "-" + (++counter));
            task.setTitle(definition[0]);
            task.setDescription("Auto-generated demo item for the " + board.getName()
                    + " board. Replace it with real work whenever you are ready.");
            task.setType(TaskType.valueOf(definition[1]));
            task.setPriority(Priority.valueOf(definition[2]));
            task.setBoard(board);
            task.setBoardColumn(column);
            task.setReporter(reporter);
            task.setAssignee(assignee);
            task.setDueDate(LocalDate.now().plusDays(Integer.parseInt(definition[4])));
            task.setStoryPoints((counter % 5) + 1);
            task.setOrderIndex((int) taskRepository.countByBoardColumnId(column.getId()));
            task.getWatchers().add(watcher);
            task.getLabels().add(board.getBoardKey().toLowerCase());
            taskRepository.save(task);
        }
        board.setTaskCounter(counter);
        boardRepository.save(board);
    }
}
