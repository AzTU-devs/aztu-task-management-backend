package az.aztu.kanban.dto;

import az.aztu.kanban.domain.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PlatformDtos {

    private PlatformDtos() {
    }

    public record PlatformDto(
            Long id,
            String name,
            String code,
            String description,
            String color,
            String icon,
            boolean active,
            UserDtos.UserSummary owner,
            long boardCount,
            long taskCount,
            Instant createdAt
    ) {
        public static PlatformDto from(Platform platform, long boardCount, long taskCount) {
            return new PlatformDto(
                    platform.getId(),
                    platform.getName(),
                    platform.getCode(),
                    platform.getDescription(),
                    platform.getColor(),
                    platform.getIcon(),
                    platform.isActive(),
                    UserDtos.UserSummary.from(platform.getOwner()),
                    boardCount,
                    taskCount,
                    platform.getCreatedAt());
        }
    }

    public record PlatformRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 20)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code may only contain letters, digits, - and _")
            String code,
            @Size(max = 500) String description,
            @Size(max = 20) String color,
            @Size(max = 40) String icon,
            Long ownerId,
            Boolean active
    ) {
    }
}
