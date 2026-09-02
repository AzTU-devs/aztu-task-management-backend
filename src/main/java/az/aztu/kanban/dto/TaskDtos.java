package az.aztu.kanban.dto;

import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record TaskCard(
            Long id,
            String taskKey,
            String title,
            TaskType type,
            Priority priority,
            Long columnId,
            ColumnCategory category,
            int orderIndex,
            UserDtos.UserSummary assignee,
            LocalDate dueDate,
            boolean overdue,
            Integer storyPoints,
            Set<String> labels,
            long commentCount,
            Instant updatedAt
    ) {
        public static TaskCard from(Task task, long commentCount) {
            LocalDate due = task.getDueDate();
            boolean overdue = due != null
                    && task.getBoardColumn().getCategory() != ColumnCategory.DONE
                    && due.isBefore(LocalDate.now());
            return new TaskCard(
                    task.getId(),
                    task.getTaskKey(),
                    task.getTitle(),
                    task.getType(),
                    task.getPriority(),
                    task.getBoardColumn().getId(),
                    task.getBoardColumn().getCategory(),
                    task.getOrderIndex(),
                    UserDtos.UserSummary.from(task.getAssignee()),
                    due,
                    overdue,
                    task.getStoryPoints(),
                    task.getLabels(),
                    commentCount,
                    task.getUpdatedAt());
        }
    }

    public record TaskDetail(
            Long id,
            String taskKey,
            String title,
            String description,
            TaskType type,
            Priority priority,
            Long boardId,
            String boardName,
            String boardKey,
            Long columnId,
            String columnName,
            ColumnCategory category,
            UserDtos.UserSummary assignee,
            UserDtos.UserSummary reporter,
            List<UserDtos.UserSummary> watchers,
            Set<String> labels,
            LocalDate startDate,
            LocalDate dueDate,
            boolean overdue,
            Integer storyPoints,
            Double estimateHours,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 250) String title,
            String description,
            @NotNull Long boardId,
            Long columnId,
            TaskType type,
            Priority priority,
            Long assigneeId,
            LocalDate startDate,
            LocalDate dueDate,
            Integer storyPoints,
            Double estimateHours,
            Set<String> labels,
            List<Long> watcherIds
    ) {
    }

    public record UpdateTaskRequest(
            @NotBlank @Size(max = 250) String title,
            String description,
            TaskType type,
            Priority priority,
            Long columnId,
            Long assigneeId,
            LocalDate startDate,
            LocalDate dueDate,
            Integer storyPoints,
            Double estimateHours,
            Set<String> labels,
            List<Long> watcherIds
    ) {
    }

    public record MoveTaskRequest(
            @NotNull Long columnId,
            @NotNull Integer position
    ) {
    }
}
