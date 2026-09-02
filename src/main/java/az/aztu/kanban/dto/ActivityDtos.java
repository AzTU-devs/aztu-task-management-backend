package az.aztu.kanban.dto;

import az.aztu.kanban.domain.Activity;
import az.aztu.kanban.domain.ActivityType;

import java.time.Instant;

public final class ActivityDtos {

    private ActivityDtos() {
    }

    public record ActivityDto(
            Long id,
            ActivityType type,
            String taskKey,
            String taskTitle,
            String boardKey,
            UserDtos.UserSummary actor,
            String field,
            String oldValue,
            String newValue,
            Instant createdAt
    ) {
        public static ActivityDto from(Activity activity) {
            return new ActivityDto(
                    activity.getId(),
                    activity.getType(),
                    activity.getTaskKey(),
                    activity.getTaskTitle(),
                    activity.getBoard() != null ? activity.getBoard().getBoardKey() : null,
                    UserDtos.UserSummary.from(activity.getActor()),
                    activity.getField(),
                    activity.getOldValue(),
                    activity.getNewValue(),
                    activity.getCreatedAt());
        }
    }
}
