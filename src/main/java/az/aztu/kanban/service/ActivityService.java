package az.aztu.kanban.service;

import az.aztu.kanban.domain.Activity;
import az.aztu.kanban.domain.ActivityType;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.ActivityDtos.ActivityDto;
import az.aztu.kanban.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;

    @Transactional
    public void log(ActivityType type, Task task, User actor, String field, String oldValue, String newValue) {
        Activity activity = new Activity();
        activity.setType(type);
        activity.setTask(task);
        activity.setActor(actor);
        activity.setField(field);
        activity.setOldValue(truncate(oldValue));
        activity.setNewValue(truncate(newValue));
        if (task != null) {
            activity.setTaskKey(task.getTaskKey());
            activity.setTaskTitle(task.getTitle());
            activity.setBoard(task.getBoard());
        }
        activityRepository.save(activity);
    }

    /** Logged after the task row itself is gone, so the task reference stays null. */
    @Transactional
    public void logTaskDeleted(String taskKey, String taskTitle, az.aztu.kanban.domain.Board board, User actor) {
        Activity activity = new Activity();
        activity.setType(ActivityType.TASK_DELETED);
        activity.setTaskKey(taskKey);
        activity.setTaskTitle(taskTitle);
        activity.setBoard(board);
        activity.setActor(actor);
        activityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> forTask(Long taskId) {
        return activityRepository.findTop50ByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(ActivityDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> recent(int limit) {
        return activityRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .getContent().stream()
                .map(ActivityDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> recentForBoard(Long boardId, int limit) {
        return activityRepository.findAllByBoardIdOrderByCreatedAtDesc(boardId, PageRequest.of(0, limit))
                .getContent().stream()
                .map(ActivityDto::from)
                .toList();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 480 ? value.substring(0, 480) + "..." : value;
    }
}
