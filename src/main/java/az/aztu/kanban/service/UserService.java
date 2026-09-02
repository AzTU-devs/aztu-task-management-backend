package az.aztu.kanban.service;

import az.aztu.kanban.domain.Role;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.PageResponse;
import az.aztu.kanban.dto.UserDtos.CreateUserRequest;
import az.aztu.kanban.dto.UserDtos.CreateUserResponse;
import az.aztu.kanban.dto.UserDtos.TempPasswordResponse;
import az.aztu.kanban.dto.UserDtos.UpdateProfileRequest;
import az.aztu.kanban.dto.UserDtos.UpdateUserRequest;
import az.aztu.kanban.dto.UserDtos.UserDto;
import az.aztu.kanban.dto.UserDtos.UserSummary;
import az.aztu.kanban.exception.BadRequestException;
import az.aztu.kanban.exception.ConflictException;
import az.aztu.kanban.exception.NotFoundException;
import az.aztu.kanban.domain.Activity;
import az.aztu.kanban.domain.Board;
import az.aztu.kanban.domain.Platform;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.repository.ActivityRepository;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.NotificationRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.TaskCommentRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String[] AVATAR_COLORS = {
            "#6366f1", "#0ea5e9", "#14b8a6", "#f59e0b", "#ef4444",
            "#8b5cf6", "#ec4899", "#10b981", "#f97316", "#3b82f6"
    };

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final PlatformRepository platformRepository;
    private final ActivityRepository activityRepository;
    private final TaskCommentRepository commentRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final MailService mailService;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String search, Role role, Boolean active, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<User> page = userRepository.search(term, role, active, pageable);
        return PageResponse.of(page, UserDto::from);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> activeSummaries() {
        return userRepository.findAllByActiveTrueOrderByFullNameAsc().stream()
                .map(UserSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public User getEntity(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    @Transactional
    public CreateUserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with e-mail " + email + " already exists.");
        }

        boolean generated = request.password() == null || request.password().isBlank();
        String rawPassword = generated ? passwordGenerator.generate() : request.password();

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setTitle(request.title());
        user.setDepartment(request.department());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setAvatarColor(colorFor(email));
        user.setMustChangePassword(generated);
        user.setActive(true);
        userRepository.save(user);

        mailService.sendWelcome(user.getEmail(), user.getFullName(), rawPassword, user.getRole().name());

        return new CreateUserResponse(UserDto.from(user), rawPassword, mailEnabled);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request, Long actingUserId) {
        User user = getEntity(id);
        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN && countAdmins() <= 1) {
            throw new BadRequestException("The last administrator cannot lose the ADMIN role.");
        }
        if (user.getId().equals(actingUserId) && Boolean.FALSE.equals(request.active())) {
            throw new BadRequestException("You cannot deactivate your own account.");
        }
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setTitle(request.title());
        user.setDepartment(request.department());
        user.setPhone(request.phone());
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.emailNotifications() != null) {
            user.setEmailNotifications(request.emailNotifications());
        }
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getEntity(userId);
        user.setFullName(request.fullName().trim());
        user.setTitle(request.title());
        user.setDepartment(request.department());
        user.setPhone(request.phone());
        if (request.emailNotifications() != null) {
            user.setEmailNotifications(request.emailNotifications());
        }
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto setActive(Long id, boolean active, Long actingUserId) {
        if (id.equals(actingUserId) && !active) {
            throw new BadRequestException("You cannot deactivate your own account.");
        }
        User user = getEntity(id);
        if (!active && user.getRole() == Role.ADMIN && countActiveAdmins() <= 1) {
            throw new BadRequestException("At least one active administrator must remain.");
        }
        user.setActive(active);
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public TempPasswordResponse resetPassword(Long id) {
        User user = getEntity(id);
        String rawPassword = passwordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        mailService.sendPasswordReset(user.getEmail(), user.getFullName(), rawPassword);
        return new TempPasswordResponse(user.getEmail(), rawPassword, mailEnabled);
    }

    @Transactional
    public void delete(Long id, Long actingUserId) {
        if (id.equals(actingUserId)) {
            throw new BadRequestException("You cannot delete your own account.");
        }
        User user = getEntity(id);
        if (user.getRole() == Role.ADMIN && countAdmins() <= 1) {
            throw new BadRequestException("The last administrator cannot be deleted.");
        }
        if (taskRepository.countByAssigneeId(id) > 0) {
            throw new BadRequestException(
                    "This user still has assigned tasks. Reassign them first or deactivate the account instead.");
        }

        // Detach every remaining reference so the row can actually be removed.
        List<Board> ledBoards = boardRepository.findAllByLeadId(id);
        ledBoards.forEach(board -> board.setLead(null));
        boardRepository.saveAll(ledBoards);

        List<Board> memberBoards = boardRepository.findAllByMemberId(id);
        memberBoards.forEach(board -> board.getMembers().removeIf(member -> member.getId().equals(id)));
        boardRepository.saveAll(memberBoards);

        List<Platform> ownedPlatforms = platformRepository.findAllByOwnerId(id);
        ownedPlatforms.forEach(platform -> platform.setOwner(null));
        platformRepository.saveAll(ownedPlatforms);

        List<Task> watched = taskRepository.findAllByWatcherId(id);
        watched.forEach(task -> task.getWatchers().removeIf(watcher -> watcher.getId().equals(id)));
        List<Task> reported = taskRepository.findAllByReporterId(id);
        reported.forEach(task -> task.setReporter(null));
        taskRepository.saveAll(watched);
        taskRepository.saveAll(reported);

        List<Activity> activities = activityRepository.findAllByActorId(id);
        activities.forEach(activity -> activity.setActor(null));
        activityRepository.saveAll(activities);

        commentRepository.deleteAllByAuthorId(id);
        notificationRepository.deleteAllByRecipientId(id);

        userRepository.delete(user);
    }

    private long countAdmins() {
        return userRepository.countByRole(Role.ADMIN);
    }

    private long countActiveAdmins() {
        return userRepository.findAllByActiveTrueOrderByFullNameAsc().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();
    }

    private String colorFor(String seed) {
        int hash = Math.abs(seed.hashCode());
        return AVATAR_COLORS[hash % AVATAR_COLORS.length];
    }
}
