package az.aztu.kanban.config;

import az.aztu.kanban.domain.ArchNodeKind;
import az.aztu.kanban.domain.ArchNoteKind;
import az.aztu.kanban.domain.ArchNoteStatus;
import az.aztu.kanban.domain.ArchitectureDiagram;
import az.aztu.kanban.domain.ArchitectureEdge;
import az.aztu.kanban.domain.ArchitectureNode;
import az.aztu.kanban.domain.Board;
import az.aztu.kanban.domain.DiagramStatus;
import az.aztu.kanban.domain.BoardColumn;
import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.Platform;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.TaskType;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.repository.ArchitectureDiagramRepository;
import az.aztu.kanban.repository.ArchitectureEdgeRepository;
import az.aztu.kanban.repository.ArchitectureNodeRepository;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import az.aztu.kanban.service.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private final ArchitectureDiagramRepository diagramRepository;
    private final ArchitectureNodeRepository nodeRepository;
    private final ArchitectureEdgeRepository edgeRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrapProperties;
    private final PasswordGenerator passwordGenerator;

    @Value("${app.seed.demo-data}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) {
        List<User> admins = ensureBootstrapAdmins();
        if (admins.isEmpty()) {
            log.error("No usable bootstrap administrator is configured - set ADMIN_EMAIL and ADMIN_PASSWORD.");
            return;
        }
        User admin = admins.get(0);

        if (!seedDemoData) {
            return;
        }

        // The architecture seed has its OWN guard rather than sitting after the workspace check.
        // An installation that already has platforms skips the workspace block entirely, and the
        // example diagram would then never appear on any real installation.
        if (platformRepository.count() > 0) {
            seedArchitecture(admin);
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
        seedArchitecture(admin);
        log.info("Demo people were created with random passwords and cannot be signed in as. "
                + "Use Users -> reset password to issue one, or set SEED_DEMO_DATA=false to skip them.");
    }

    /**
     * Creates every configured administrator that does not exist yet.
     *
     * An account that already exists is never overwritten: its password stays untouched and,
     * crucially, a deactivated account stays deactivated. Re-enabling it here would silently
     * undo an offboarding on the next restart while the old password still worked. Only a
     * still-active account that is not an administrator yet gets promoted.
     */
    private List<User> ensureBootstrapAdmins() {
        List<User> admins = new ArrayList<>();
        Set<String> handled = new LinkedHashSet<>();

        for (BootstrapProperties.AdminAccount account : bootstrapProperties.usableAdmins()) {
            String email = account.normalizedEmail();
            if (!handled.add(email)) {
                log.warn("Bootstrap administrator {} is configured more than once - ignoring the duplicate.", email);
                continue;
            }

            User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (existing != null) {
                if (!existing.isActive()) {
                    log.warn("Bootstrap slot {} points at a deactivated account - leaving it deactivated. "
                            + "Clear that slot in .env if the person has left.", email);
                    continue;
                }
                if (existing.getRole() != Role.ADMIN) {
                    existing.setRole(Role.ADMIN);
                    userRepository.save(existing);
                    log.info("Existing account promoted to administrator: {}", email);
                }
                admins.add(existing);
                continue;
            }

            User admin = new User();
            admin.setEmail(email);
            admin.setFullName(account.displayName());
            admin.setPasswordHash(passwordEncoder.encode(account.getPassword()));
            admin.setRole(Role.ADMIN);
            admin.setTitle(account.getTitle());
            admin.setDepartment(account.getDepartment());
            admin.setAvatarColor("#6366f1");
            admin.setActive(true);
            userRepository.save(admin);
            admins.add(admin);
            log.info("Bootstrap administrator created: {}", email);
        }
        return admins;
    }

    /** One worked example so the Architecture page is not empty on the first visit. */
    private void seedArchitecture(User author) {
        if (diagramRepository.count() > 0) {
            return;
        }
        Platform platform = platformRepository.findAll().stream().findFirst().orElse(null);
        if (platform == null) {
            return;
        }

        ArchitectureDiagram diagram = new ArchitectureDiagram();
        diagram.setName("Student Portal architecture");
        diagram.setDescription("How the student-facing portal is put together and what it depends on.");
        diagram.setPlatform(platform);
        diagram.setOwner(author);
        diagram.setStatus(DiagramStatus.APPROVED);
        diagramRepository.save(diagram);

        ArchitectureNode portal = archNode(diagram, "Student Portal", ArchNodeKind.COMPONENT,
                "Next.js", "Where students check grades, schedules and documents.", 120, 120, 240, 110);
        ArchitectureNode gateway = archNode(diagram, "API Gateway", ArchNodeKind.SERVICE,
                "Spring Cloud Gateway", null, 520, 120, 240, 110);
        ArchitectureNode exams = archNode(diagram, "Exam Service", ArchNodeKind.SERVICE,
                "Spring Boot 3.3 / Java 21", null, 920, 40, 240, 110);
        ArchitectureNode identity = archNode(diagram, "Identity Service", ArchNodeKind.SERVICE,
                "Keycloak", null, 920, 220, 240, 110);
        ArchitectureNode database = archNode(diagram, "Academic database", ArchNodeKind.DATABASE,
                "PostgreSQL 16", null, 1320, 130, 240, 110);
        ArchitectureNode ministry = archNode(diagram, "Ministry reporting", ArchNodeKind.EXTERNAL,
                "SOAP", "Statutory reporting endpoint outside the university.", 1320, 340, 240, 110);

        ArchitectureNode note = archNode(diagram, "One gateway in front of every service",
                ArchNodeKind.NOTE, null,
                "Students reach exactly one public host. Authentication and rate limiting live in "
                        + "the gateway so no individual service has to repeat them.",
                120, 340, 300, 150);
        note.setNoteKind(ArchNoteKind.DECISION);
        note.setNoteStatus(ArchNoteStatus.ACCEPTED);
        note.setDecidedOn(LocalDate.now().minusMonths(2));
        note.setAuthor(author);
        nodeRepository.save(note);

        archEdge(diagram, portal, gateway, "all requests", "HTTPS/JSON", false);
        archEdge(diagram, gateway, exams, "results, timetables", "HTTPS/JSON", false);
        archEdge(diagram, gateway, identity, "sign in", "OIDC", false);
        archEdge(diagram, exams, database, "reads and writes", "JDBC", false);
        archEdge(diagram, identity, database, "accounts", "JDBC", false);
        archEdge(diagram, exams, ministry, "nightly export", "SOAP", true);

        log.info("Seeded the example architecture diagram.");
    }

    private ArchitectureNode archNode(ArchitectureDiagram diagram, String name, ArchNodeKind kind,
                                      String technology, String description,
                                      int x, int y, int width, int height) {
        ArchitectureNode node = new ArchitectureNode();
        node.setDiagram(diagram);
        node.setName(name);
        node.setKind(kind);
        node.setTechnology(technology);
        node.setDescription(description);
        node.setX(x);
        node.setY(y);
        node.setWidth(width);
        node.setHeight(height);
        return nodeRepository.save(node);
    }

    private void archEdge(ArchitectureDiagram diagram, ArchitectureNode source, ArchitectureNode target,
                          String label, String technology, boolean dashed) {
        ArchitectureEdge edge = new ArchitectureEdge();
        edge.setDiagram(diagram);
        edge.setSourceNode(source);
        edge.setTargetNode(target);
        edge.setLabel(label);
        edge.setTechnology(technology);
        edge.setDashed(dashed);
        edgeRepository.save(edge);
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
            // A password that exists in tracked source would be a real login for anyone who
            // reads the repository. These demo people are assignees, not accounts to sign in
            // as: give them an unguessable secret nobody holds. An administrator can issue a
            // real password from Users -> reset password if one of them should become usable.
            user.setPasswordHash(passwordEncoder.encode(passwordGenerator.generate(24)));
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
