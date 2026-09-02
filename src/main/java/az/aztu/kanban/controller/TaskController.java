package az.aztu.kanban.controller;

import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.TaskType;
import az.aztu.kanban.dto.ActivityDtos.ActivityDto;
import az.aztu.kanban.dto.CommentDtos.CommentDto;
import az.aztu.kanban.dto.CommentDtos.CommentRequest;
import az.aztu.kanban.dto.PageResponse;
import az.aztu.kanban.dto.TaskDtos.CreateTaskRequest;
import az.aztu.kanban.dto.TaskDtos.MoveTaskRequest;
import az.aztu.kanban.dto.TaskDtos.TaskCard;
import az.aztu.kanban.dto.TaskDtos.TaskDetail;
import az.aztu.kanban.dto.TaskDtos.UpdateTaskRequest;
import az.aztu.kanban.security.UserPrincipal;
import az.aztu.kanban.service.ActivityService;
import az.aztu.kanban.service.CommentService;
import az.aztu.kanban.service.TaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;
    private final CommentService commentService;
    private final ActivityService activityService;

    @GetMapping
    public PageResponse<TaskCard> search(@RequestParam(required = false) Long boardId,
                                         @RequestParam(required = false) Long assigneeId,
                                         @RequestParam(required = false) Long reporterId,
                                         @RequestParam(defaultValue = "false") boolean mine,
                                         @RequestParam(required = false) TaskType type,
                                         @RequestParam(required = false) Priority priority,
                                         @RequestParam(required = false) ColumnCategory category,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        Long assignee = mine ? principal.getId() : assigneeId;
        return taskService.search(boardId, assignee, reporterId, type, priority, category, search,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @GetMapping("/{id}")
    public TaskDetail get(@PathVariable Long id) {
        return taskService.get(id);
    }

    @GetMapping("/key/{taskKey}")
    public TaskDetail getByKey(@PathVariable String taskKey) {
        return taskService.getByKey(taskKey);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDetail create(@Valid @RequestBody CreateTaskRequest request,
                             @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.create(request, principal.getUser());
    }

    @PutMapping("/{id}")
    public TaskDetail update(@PathVariable Long id,
                             @Valid @RequestBody UpdateTaskRequest request,
                             @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.update(id, request, principal.getUser());
    }

    @PatchMapping("/{id}/move")
    public TaskCard move(@PathVariable Long id,
                         @Valid @RequestBody MoveTaskRequest request,
                         @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.move(id, request, principal.getUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        taskService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/activity")
    public List<ActivityDto> activity(@PathVariable Long id) {
        return activityService.forTask(id);
    }

    @GetMapping("/{id}/comments")
    public List<CommentDto> comments(@PathVariable Long id) {
        return commentService.list(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@PathVariable Long id,
                                 @Valid @RequestBody CommentRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return commentService.add(id, request, principal.getUser());
    }
}
