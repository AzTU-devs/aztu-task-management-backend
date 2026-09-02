package az.aztu.kanban.service;

import az.aztu.kanban.domain.NotificationType;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Sends a due-date reminder every morning for tasks that are due today or tomorrow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Scheduled(cron = "${app.reminders.cron:0 0 8 * * *}", zone = "Asia/Baku")
    @Transactional
    public void sendDueReminders() {
        LocalDate today = LocalDate.now();
        List<Task> due = new java.util.ArrayList<>(taskRepository.findDueOn(today));
        due.addAll(taskRepository.findDueOn(today.plusDays(1)));

        log.info("Due-date reminder job: {} task(s) to notify", due.size());
        for (Task task : due) {
            User assignee = task.getAssignee();
            if (assignee == null || !assignee.isActive()) {
                continue;
            }
            notificationService.push(assignee, NotificationType.DUE_SOON,
                    task.getTaskKey() + " is due " + (task.getDueDate().equals(today) ? "today" : "tomorrow"),
                    "\"" + task.getTitle() + "\" is due on " + task.getDueDate() + ".",
                    "/tasks/" + task.getTaskKey());
            if (assignee.isEmailNotifications()) {
                mailService.sendDueReminder(assignee.getEmail(), assignee.getFullName(),
                        task.getTaskKey(), task.getTitle(), task.getDueDate().toString(),
                        task.getBoard().getName());
            }
        }
    }
}
