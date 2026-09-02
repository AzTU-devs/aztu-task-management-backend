package az.aztu.kanban.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * All outgoing e-mail. Every method is fire-and-forget: a broken SMTP setup can never
 * break the request that triggered the notification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.enabled}")
    private boolean enabled;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public String taskLink(String taskKey) {
        return frontendUrl.replaceAll("/+$", "") + "/tasks/" + taskKey;
    }

    public String boardLink(String boardKey) {
        return frontendUrl.replaceAll("/+$", "") + "/boards/" + boardKey;
    }

    public String loginLink() {
        return frontendUrl.replaceAll("/+$", "") + "/login";
    }

    @Async("mailExecutor")
    public void sendWelcome(String to, String fullName, String temporaryPassword, String role) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", fullName);
        vars.put("email", to);
        vars.put("password", temporaryPassword);
        vars.put("role", role);
        vars.put("loginUrl", loginLink());
        send(to, "Your AzTU Kanban account is ready", "email/welcome", vars);
    }

    @Async("mailExecutor")
    public void sendPasswordReset(String to, String fullName, String temporaryPassword) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", fullName);
        vars.put("email", to);
        vars.put("password", temporaryPassword);
        vars.put("loginUrl", loginLink());
        send(to, "Your AzTU Kanban password was reset", "email/password-reset", vars);
    }

    @Async("mailExecutor")
    public void sendTaskAssigned(String to, String recipientName, String taskKey, String title,
                                 String boardName, String priority, String dueDate, String actorName) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", recipientName);
        vars.put("taskKey", taskKey);
        vars.put("title", title);
        vars.put("boardName", boardName);
        vars.put("priority", priority);
        vars.put("dueDate", dueDate);
        vars.put("actorName", actorName);
        vars.put("taskUrl", taskLink(taskKey));
        send(to, "[" + taskKey + "] assigned to you - " + title, "email/task-assigned", vars);
    }

    @Async("mailExecutor")
    public void sendComment(String to, String recipientName, String taskKey, String title,
                            String authorName, String body) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", recipientName);
        vars.put("taskKey", taskKey);
        vars.put("title", title);
        vars.put("authorName", authorName);
        vars.put("body", body);
        vars.put("taskUrl", taskLink(taskKey));
        send(to, "[" + taskKey + "] new comment from " + authorName, "email/comment-added", vars);
    }

    @Async("mailExecutor")
    public void sendStatusChanged(String to, String recipientName, String taskKey, String title,
                                  String fromColumn, String toColumn, String actorName) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", recipientName);
        vars.put("taskKey", taskKey);
        vars.put("title", title);
        vars.put("fromColumn", fromColumn);
        vars.put("toColumn", toColumn);
        vars.put("actorName", actorName);
        vars.put("taskUrl", taskLink(taskKey));
        send(to, "[" + taskKey + "] moved to " + toColumn, "email/status-changed", vars);
    }

    @Async("mailExecutor")
    public void sendDueReminder(String to, String recipientName, String taskKey, String title,
                                String dueDate, String boardName) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", recipientName);
        vars.put("taskKey", taskKey);
        vars.put("title", title);
        vars.put("dueDate", dueDate);
        vars.put("boardName", boardName);
        vars.put("taskUrl", taskLink(taskKey));
        send(to, "[" + taskKey + "] is due on " + dueDate, "email/due-reminder", vars);
    }

    private void send(String to, String subject, String template, Map<String, Object> variables) {
        if (!enabled) {
            log.info("[mail disabled] would send '{}' to {}", subject, to);
            return;
        }
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("appName", "AzTU Kanban");
            String html = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            try {
                helper.setFrom(from, fromName);
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(from);
            }
            mailSender.send(message);
            log.info("Sent '{}' to {}", subject, to);
        } catch (Exception ex) {
            log.error("Failed to send '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }
}
