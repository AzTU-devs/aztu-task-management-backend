package az.aztu.kanban.service;

import az.aztu.kanban.domain.Notification;
import az.aztu.kanban.domain.NotificationType;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.NotificationDtos.NotificationDto;
import az.aztu.kanban.exception.NotFoundException;
import az.aztu.kanban.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void push(User recipient, NotificationType type, String title, String message, String link) {
        if (recipient == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long userId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findTop50ByRecipientIdAndReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(userId);
        return notifications.stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw NotFoundException.of("Notification", notificationId);
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }
}
