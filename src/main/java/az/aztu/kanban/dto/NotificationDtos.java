package az.aztu.kanban.dto;

import az.aztu.kanban.domain.Notification;
import az.aztu.kanban.domain.NotificationType;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationDto(
            Long id,
            NotificationType type,
            String title,
            String message,
            String link,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationDto from(Notification notification) {
            return new NotificationDto(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getLink(),
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }
}
