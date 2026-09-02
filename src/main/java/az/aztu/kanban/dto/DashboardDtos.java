package az.aztu.kanban.dto;

import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Counter(String label, long value) {
    }

    public record DashboardStats(
            long totalPlatforms,
            long totalBoards,
            long totalTasks,
            long totalUsers,
            long myOpenTasks,
            long myOverdueTasks,
            long todoCount,
            long inProgressCount,
            long doneCount,
            Map<String, Long> tasksByPriority,
            Map<String, Long> tasksByType,
            List<TaskDtos.TaskCard> myTasks,
            List<TaskDtos.TaskCard> upcomingDeadlines,
            List<ActivityDtos.ActivityDto> recentActivity,
            List<BoardDtos.BoardSummary> boards
    ) {
    }
}
