package az.aztu.kanban.service;

import az.aztu.kanban.domain.ActivityType;
import az.aztu.kanban.domain.NotificationType;
import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.TaskComment;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.CommentDtos.CommentDto;
import az.aztu.kanban.dto.CommentDtos.CommentRequest;
import az.aztu.kanban.exception.ForbiddenException;
import az.aztu.kanban.exception.NotFoundException;
import az.aztu.kanban.repository.TaskCommentRepository;
import az.aztu.kanban.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final TaskCommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final MailService mailService;

    @Transactional(readOnly = true)
    public List<CommentDto> list(Long taskId) {
        return commentRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(CommentDto::from)
                .toList();
    }

    @Transactional
    public CommentDto add(Long taskId, CommentRequest request, User author) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setBody(request.body().trim());
        commentRepository.save(comment);

        activityService.log(ActivityType.COMMENT_ADDED, task, author, "comment", null, preview(comment.getBody()));

        Set<User> recipients = new LinkedHashSet<>();
        if (task.getAssignee() != null) {
            recipients.add(task.getAssignee());
        }
        if (task.getReporter() != null) {
            recipients.add(task.getReporter());
        }
        recipients.addAll(task.getWatchers());

        for (User recipient : recipients) {
            if (recipient.getId().equals(author.getId())) {
                continue;
            }
            notificationService.push(recipient, NotificationType.COMMENT_ADDED,
                    "New comment on " + task.getTaskKey(),
                    author.getFullName() + ": " + preview(comment.getBody()),
                    "/tasks/" + task.getTaskKey());
            if (recipient.isEmailNotifications()) {
                mailService.sendComment(recipient.getEmail(), recipient.getFullName(),
                        task.getTaskKey(), task.getTitle(), author.getFullName(), comment.getBody());
            }
        }

        return CommentDto.from(comment);
    }

    @Transactional
    public CommentDto update(Long commentId, CommentRequest request, User user) {
        TaskComment comment = getEntity(commentId);
        requireOwnerOrAdmin(comment, user);
        comment.setBody(request.body().trim());
        comment.setEdited(true);
        return CommentDto.from(commentRepository.save(comment));
    }

    @Transactional
    public void delete(Long commentId, User user) {
        TaskComment comment = getEntity(commentId);
        requireOwnerOrAdmin(comment, user);
        commentRepository.delete(comment);
    }

    private TaskComment getEntity(Long id) {
        return commentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Comment", id));
    }

    private void requireOwnerOrAdmin(TaskComment comment, User user) {
        if (!comment.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only the author or an administrator can change this comment.");
        }
    }

    private String preview(String body) {
        return body.length() > 140 ? body.substring(0, 140) + "..." : body;
    }
}
