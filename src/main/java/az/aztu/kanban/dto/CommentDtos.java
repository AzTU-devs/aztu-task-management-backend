package az.aztu.kanban.dto;

import az.aztu.kanban.domain.TaskComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CommentDtos {

    private CommentDtos() {
    }

    public record CommentDto(
            Long id,
            String body,
            UserDtos.UserSummary author,
            boolean edited,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CommentDto from(TaskComment comment) {
            return new CommentDto(
                    comment.getId(),
                    comment.getBody(),
                    UserDtos.UserSummary.from(comment.getAuthor()),
                    comment.isEdited(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt());
        }
    }

    public record CommentRequest(
            @NotBlank @Size(max = 5000) String body
    ) {
    }
}
