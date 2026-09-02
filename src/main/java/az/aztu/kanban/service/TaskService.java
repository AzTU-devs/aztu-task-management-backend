package az.aztu.kanban.service;

import az.aztu.kanban.domain.ActivityType;
import az.aztu.kanban.domain.Board;
import az.aztu.kanban.domain.BoardColumn;
import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.NotificationType;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.TaskType;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.PageResponse;
import az.aztu.kanban.dto.TaskDtos.CreateTaskRequest;
import az.aztu.kanban.dto.TaskDtos.MoveTaskRequest;
import az.aztu.kanban.dto.TaskDtos.TaskCard;
import az.aztu.kanban.dto.TaskDtos.TaskDetail;
import az.aztu.kanban.dto.TaskDtos.UpdateTaskRequest;
import az.aztu.kanban.dto.UserDtos.UserSummary;
import az.aztu.kanban.exception.BadRequestException;
import az.aztu.kanban.exception.NotFoundException;
import az.aztu.kanban.repository.ActivityRepository;
import az.aztu.kanban.repository.BoardColumnRepository;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.TaskCommentRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final UserRepository userRepository;
    private final TaskCommentRepository commentRepository;
    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final MailService mailService;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Task getEntity(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> NotFoundException.of("Task", id));
    }

    @Transactional(readOnly = true)
    public TaskDetail get(Long id) {
        return toDetail(getEntity(id));
    }

    @Transactional(readOnly = true)
    public TaskDetail getByKey(String taskKey) {
        Task task = taskRepository.findByTaskKeyIgnoreCase(taskKey)
                .orElseThrow(() -> NotFoundException.of("Task", taskKey));
        return toDetail(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskCard> search(Long boardId, Long assigneeId, Long reporterId, TaskType type,
                                         Priority priority, ColumnCategory category, String search,
                                         Pageable pageable) {
        Page<Task> page = taskRepository.search(boardId, assigneeId, reporterId, type, priority, category,
                (search == null || search.isBlank()) ? null : search.trim(), pageable);
        return PageResponse.of(page, task -> TaskCard.from(task, commentRepository.countByTaskId(task.getId())));
    }

    // ---------------------------------------------------------------- mutations

    @Transactional
    public TaskDetail create(CreateTaskRequest request, User actor) {
        Board board = boardRepository.findById(request.boardId())
                .orElseThrow(() -> NotFoundException.of("Board", request.boardId()));

        BoardColumn column;
        if (request.columnId() != null) {
            column = requireColumnOfBoard(request.columnId(), board.getId());
        } else {
            column = columnRepository.findFirstByBoardIdOrderByPositionAsc(board.getId())
                    .orElseThrow(() -> new BadRequestException("This board has no columns yet."));
        }

        board.setTaskCounter(board.getTaskCounter() + 1);
        boardRepository.save(board);

        Task task = new Task();
        task.setTaskKey(board.getBoardKey() + "-" + board.getTaskCounter());
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setType(request.type() == null ? TaskType.TASK : request.type());
        task.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        task.setBoard(board);
        task.setBoardColumn(column);
        task.setReporter(actor);
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        task.setStoryPoints(request.storyPoints());
        task.setEstimateHours(request.estimateHours());
        task.setLabels(cleanLabels(request.labels()));
        task.setOrderIndex((int) taskRepository.countByBoardColumnId(column.getId()));

        if (request.assigneeId() != null) {
            task.setAssignee(userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> NotFoundException.of("User", request.assigneeId())));
        }
        task.setWatchers(resolveUsers(request.watcherIds()));
        if (column.getCategory() == ColumnCategory.DONE) {
            task.setCompletedAt(Instant.now());
        }

        taskRepository.save(task);
        activityService.log(ActivityType.TASK_CREATED, task, actor, null, null, task.getTitle());

        if (task.getAssignee() != null) {
            notifyAssignment(task, actor);
        }
        return toDetail(task);
    }

    @Transactional
    public TaskDetail update(Long id, UpdateTaskRequest request, User actor) {
        Task task = getEntity(id);

        String previousTitle = task.getTitle();
        Priority previousPriority = task.getPriority();
        TaskType previousType = task.getType();
        LocalDate previousDueDate = task.getDueDate();
        User previousAssignee = task.getAssignee();
        BoardColumn previousColumn = task.getBoardColumn();

        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        if (request.type() != null) {
            task.setType(request.type());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        task.setStoryPoints(request.storyPoints());
        task.setEstimateHours(request.estimateHours());
        task.setLabels(cleanLabels(request.labels()));
        if (request.watcherIds() != null) {
            task.setWatchers(resolveUsers(request.watcherIds()));
        }

        if (request.columnId() != null && !request.columnId().equals(previousColumn.getId())) {
            BoardColumn target = requireColumnOfBoard(request.columnId(), task.getBoard().getId());
            moveToColumn(task, target, (int) taskRepository.countByBoardColumnId(target.getId()));
        }

        if (request.assigneeId() == null) {
            task.setAssignee(null);
        } else if (previousAssignee == null || !previousAssignee.getId().equals(request.assigneeId())) {
            task.setAssignee(userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> NotFoundException.of("User", request.assigneeId())));
        }

        taskRepository.save(task);

        if (!Objects.equals(previousTitle, task.getTitle())) {
            activityService.log(ActivityType.TASK_UPDATED, task, actor, "title", previousTitle, task.getTitle());
        }
        if (previousPriority != task.getPriority()) {
            activityService.log(ActivityType.TASK_UPDATED, task, actor, "priority",
                    previousPriority.name(), task.getPriority().name());
        }
        if (previousType != task.getType()) {
            activityService.log(ActivityType.TASK_UPDATED, task, actor, "type",
                    previousType.name(), task.getType().name());
        }
        if (!Objects.equals(previousDueDate, task.getDueDate())) {
            activityService.log(ActivityType.TASK_UPDATED, task, actor, "dueDate",
                    String.valueOf(previousDueDate), String.valueOf(task.getDueDate()));
        }
        if (!Objects.equals(previousColumn.getId(), task.getBoardColumn().getId())) {
            activityService.log(ActivityType.TASK_MOVED, task, actor, "status",
                    previousColumn.getName(), task.getBoardColumn().getName());
            notifyStatusChange(task, previousColumn.getName(), actor);
        }

        Long previousAssigneeId = previousAssignee == null ? null : previousAssignee.getId();
        Long newAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        if (!Objects.equals(previousAssigneeId, newAssigneeId)) {
            if (newAssigneeId == null) {
                activityService.log(ActivityType.TASK_UNASSIGNED, task, actor, "assignee",
                        previousAssignee.getFullName(), null);
            } else {
                activityService.log(ActivityType.TASK_ASSIGNED, task, actor, "assignee",
                        previousAssignee == null ? null : previousAssignee.getFullName(),
                        task.getAssignee().getFullName());
                notifyAssignment(task, actor);
            }
        }

        return toDetail(task);
    }

    @Transactional
    public TaskCard move(Long id, MoveTaskRequest request, User actor) {
        Task task = getEntity(id);
        BoardColumn target = requireColumnOfBoard(request.columnId(), task.getBoard().getId());
        String previousColumnName = task.getBoardColumn().getName();
        boolean columnChanged = !target.getId().equals(task.getBoardColumn().getId());

        if (columnChanged && target.getWipLimit() != null) {
            long current = taskRepository.countByBoardColumnId(target.getId());
            if (current >= target.getWipLimit()) {
                throw new BadRequestException(
                        "\"" + target.getName() + "\" has reached its WIP limit of " + target.getWipLimit() + ".");
            }
        }

        moveToColumn(task, target, request.position());
        taskRepository.save(task);

        if (columnChanged) {
            activityService.log(ActivityType.TASK_MOVED, task, actor, "status", previousColumnName, target.getName());
            notifyStatusChange(task, previousColumnName, actor);
        }
        return TaskCard.from(task, commentRepository.countByTaskId(task.getId()));
    }

    @Transactional
    public void delete(Long id, User actor) {
        Task task = getEntity(id);
        Long columnId = task.getBoardColumn().getId();
        String taskKey = task.getTaskKey();
        String title = task.getTitle();
        Board board = task.getBoard();

        activityRepository.deleteAllByTaskId(id);
        commentRepository.deleteAllByTaskId(id);
        task.getWatchers().clear();
        task.getLabels().clear();
        taskRepository.save(task);
        taskRepository.delete(task);
        taskRepository.flush();

        activityService.logTaskDeleted(taskKey, title, board, actor);
        reindex(columnId);
    }

    // ---------------------------------------------------------------- helpers

    private void moveToColumn(Task task, BoardColumn target, int position) {
        Long sourceColumnId = task.getBoardColumn().getId();
        boolean sameColumn = sourceColumnId.equals(target.getId());

        List<Task> targetTasks = new ArrayList<>(taskRepository.findAllByBoardColumnIdOrderByOrderIndexAsc(target.getId()));
        targetTasks.removeIf(item -> item.getId().equals(task.getId()));

        int index = Math.max(0, Math.min(position, targetTasks.size()));
        targetTasks.add(index, task);

        task.setBoardColumn(target);
        if (target.getCategory() == ColumnCategory.DONE) {
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(Instant.now());
            }
        } else {
            task.setCompletedAt(null);
        }

        for (int i = 0; i < targetTasks.size(); i++) {
            targetTasks.get(i).setOrderIndex(i);
        }
        taskRepository.saveAll(targetTasks);

        if (!sameColumn) {
            reindex(sourceColumnId);
        }
    }

    private void reindex(Long columnId) {
        List<Task> tasks = taskRepository.findAllByBoardColumnIdOrderByOrderIndexAsc(columnId);
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setOrderIndex(i);
        }
        taskRepository.saveAll(tasks);
    }

    private BoardColumn requireColumnOfBoard(Long columnId, Long boardId) {
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> NotFoundException.of("Column", columnId));
        if (!column.getBoard().getId().equals(boardId)) {
            throw new BadRequestException("That column belongs to a different board.");
        }
        return column;
    }

    private Set<User> resolveUsers(List<Long> ids) {
        Set<User> users = new LinkedHashSet<>();
        if (ids == null) {
            return users;
        }
        for (Long id : ids) {
            users.add(userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id)));
        }
        return users;
    }

    private Set<String> cleanLabels(Set<String> labels) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (labels == null) {
            return cleaned;
        }
        for (String label : labels) {
            if (label != null && !label.isBlank()) {
                cleaned.add(label.trim().toLowerCase());
            }
        }
        return cleaned;
    }

    private void notifyAssignment(Task task, User actor) {
        User assignee = task.getAssignee();
        if (assignee == null || (actor != null && assignee.getId().equals(actor.getId()))) {
            return;
        }
        String actorName = actor == null ? "Someone" : actor.getFullName();
        notificationService.push(assignee, NotificationType.TASK_ASSIGNED,
                task.getTaskKey() + " was assigned to you",
                actorName + " assigned \"" + task.getTitle() + "\" to you.",
                "/tasks/" + task.getTaskKey());

        if (assignee.isEmailNotifications()) {
            mailService.sendTaskAssigned(
                    assignee.getEmail(),
                    assignee.getFullName(),
                    task.getTaskKey(),
                    task.getTitle(),
                    task.getBoard().getName(),
                    task.getPriority().name(),
                    task.getDueDate() == null ? "Not set" : task.getDueDate().toString(),
                    actorName);
        }
    }

    private void notifyStatusChange(Task task, String previousColumnName, User actor) {
        String actorName = actor == null ? "Someone" : actor.getFullName();
        Set<User> recipients = new LinkedHashSet<>();
        if (task.getAssignee() != null) {
            recipients.add(task.getAssignee());
        }
        if (task.getReporter() != null) {
            recipients.add(task.getReporter());
        }
        recipients.addAll(task.getWatchers());

        for (User recipient : recipients) {
            if (actor != null && recipient.getId().equals(actor.getId())) {
                continue;
            }
            notificationService.push(recipient, NotificationType.TASK_MOVED,
                    task.getTaskKey() + " moved to " + task.getBoardColumn().getName(),
                    actorName + " moved \"" + task.getTitle() + "\" from " + previousColumnName
                            + " to " + task.getBoardColumn().getName() + ".",
                    "/tasks/" + task.getTaskKey());
            if (recipient.isEmailNotifications()) {
                mailService.sendStatusChanged(recipient.getEmail(), recipient.getFullName(),
                        task.getTaskKey(), task.getTitle(), previousColumnName,
                        task.getBoardColumn().getName(), actorName);
            }
        }
    }

    TaskDetail toDetail(Task task) {
        LocalDate due = task.getDueDate();
        boolean overdue = due != null
                && task.getBoardColumn().getCategory() != ColumnCategory.DONE
                && due.isBefore(LocalDate.now());

        return new TaskDetail(
                task.getId(),
                task.getTaskKey(),
                task.getTitle(),
                task.getDescription(),
                task.getType(),
                task.getPriority(),
                task.getBoard().getId(),
                task.getBoard().getName(),
                task.getBoard().getBoardKey(),
                task.getBoardColumn().getId(),
                task.getBoardColumn().getName(),
                task.getBoardColumn().getCategory(),
                UserSummary.from(task.getAssignee()),
                UserSummary.from(task.getReporter()),
                task.getWatchers().stream().map(UserSummary::from).toList(),
                new LinkedHashSet<>(task.getLabels()),
                task.getStartDate(),
                due,
                overdue,
                task.getStoryPoints(),
                task.getEstimateHours(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
