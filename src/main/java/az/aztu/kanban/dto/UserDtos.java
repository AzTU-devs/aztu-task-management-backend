package az.aztu.kanban.dto;

import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserDto(
            Long id,
            String email,
            String fullName,
            String initials,
            Role role,
            String title,
            String department,
            String phone,
            String avatarColor,
            boolean active,
            boolean mustChangePassword,
            boolean emailNotifications,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        public static UserDto from(User user) {
            if (user == null) {
                return null;
            }
            return new UserDto(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.initials(),
                    user.getRole(),
                    user.getTitle(),
                    user.getDepartment(),
                    user.getPhone(),
                    user.getAvatarColor(),
                    user.isActive(),
                    user.isMustChangePassword(),
                    user.isEmailNotifications(),
                    user.getLastLoginAt(),
                    user.getCreatedAt());
        }
    }

    public record UserSummary(
            Long id,
            String fullName,
            String email,
            String initials,
            String avatarColor,
            Role role
    ) {
        public static UserSummary from(User user) {
            if (user == null) {
                return null;
            }
            return new UserSummary(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.initials(),
                    user.getAvatarColor(),
                    user.getRole());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role,
            @Size(max = 120) String title,
            @Size(max = 120) String department,
            @Size(max = 30) String phone,
            @Size(min = 8, max = 72) String password
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role,
            @Size(max = 120) String title,
            @Size(max = 120) String department,
            @Size(max = 30) String phone,
            Boolean active,
            Boolean emailNotifications
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 120) String title,
            @Size(max = 120) String department,
            @Size(max = 30) String phone,
            Boolean emailNotifications
    ) {
    }

    public record TempPasswordResponse(String email, String temporaryPassword, boolean emailSent) {
    }

    public record CreateUserResponse(UserDto user, String temporaryPassword, boolean emailSent) {
    }
}
