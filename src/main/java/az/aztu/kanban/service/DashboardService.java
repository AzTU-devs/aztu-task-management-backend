package az.aztu.kanban.service;

import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.BoardDtos;
import az.aztu.kanban.dto.BoardDtos.BoardSummary;
import az.aztu.kanban.dto.DashboardDtos.DashboardStats;
import az.aztu.kanban.dto.TaskDtos.TaskCard;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.TaskCommentRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PlatformRepository platformRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskCommentRepository commentRepository;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public DashboardStats stats(User user) {
        boolean admin = user.getRole() == Role.ADMIN;

        LocalDate today = LocalDate.now();

        List<Task> assignedToMe = taskRepository.search(null, user.getId(), null, null, null, null, null,
                        PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "dueDate")))
                .getContent();

        List<Task> myTasks = assignedToMe.stream()
                .filter(task -> task.getBoardColumn().getCategory() != ColumnCategory.DONE)
                .limit(8)
                .toList();
        List<Task> upcoming = taskRepository.findDueOnOrBefore(today.plusDays(7)).stream()
                .filter(task -> admin || isRelated(task, user))
                .sorted(Comparator.comparing(Task::getDueDate))
                .limit(8)
                .toList();

        long myOverdue = assignedToMe.stream()
                .filter(task -> task.getDueDate() != null
                        && task.getDueDate().isBefore(today)
                        && task.getBoardColumn().getCategory() != ColumnCategory.DONE)
                .count();

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupedByPriority()) {
            byPriority.put(String.valueOf(row[0]), (Long) row[1]);
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupedByType()) {
            byType.put(String.valueOf(row[0]), (Long) row[1]);
        }

        List<BoardSummary> boards = (admin
                ? boardRepository.findAllByOrderByNameAsc()
                : boardRepository.findBoardsForUser(user.getId()))
                .stream()
                .limit(6)
                .map(board -> {
                    long total = taskRepository.countByBoardId(board.getId());
                    long done = taskRepository.findAllByBoardId(board.getId()).stream()
                            .filter(task -> task.getBoardColumn().getCategory() == ColumnCategory.DONE)
                            .count();
                    return BoardDtos.summary(board, total, done);
                })
                .toList();

        return new DashboardStats(
                platformRepository.count(),
                boardRepository.count(),
                taskRepository.count(),
                userRepository.countByActiveTrue(),
                taskRepository.countOpenForAssignee(user.getId()),
                myOverdue,
                taskRepository.countByCategory(ColumnCategory.TODO),
                taskRepository.countByCategory(ColumnCategory.IN_PROGRESS),
                taskRepository.countByCategory(ColumnCategory.DONE),
                byPriority,
                byType,
                myTasks.stream().map(task -> TaskCard.from(task, commentRepository.countByTaskId(task.getId()))).toList(),
                upcoming.stream().map(task -> TaskCard.from(task, 0)).toList(),
                activityService.recent(12),
                boards);
    }

    private boolean isRelated(Task task, User user) {
        if (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId())) {
            return true;
        }
        return task.getReporter() != null && task.getReporter().getId().equals(user.getId());
    }
}
