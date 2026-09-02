package az.aztu.kanban.controller;

import az.aztu.kanban.domain.Role;
import az.aztu.kanban.dto.PageResponse;
import az.aztu.kanban.dto.UserDtos.CreateUserRequest;
import az.aztu.kanban.dto.UserDtos.CreateUserResponse;
import az.aztu.kanban.dto.UserDtos.TempPasswordResponse;
import az.aztu.kanban.dto.UserDtos.UpdateUserRequest;
import az.aztu.kanban.dto.UserDtos.UserDto;
import az.aztu.kanban.dto.UserDtos.UserSummary;
import az.aztu.kanban.security.UserPrincipal;
import az.aztu.kanban.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    /** Lightweight list used by assignee / member pickers. Available to every signed-in user. */
    @GetMapping("/directory")
    public List<UserSummary> directory() {
        return userService.activeSummaries();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserDto> list(@RequestParam(required = false) String search,
                                      @RequestParam(required = false) Role role,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return userService.search(search, role, active,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "fullName")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@PathVariable Long id,
                          @Valid @RequestBody UpdateUserRequest request,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return userService.update(id, request, principal.getId());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto setActive(@PathVariable Long id,
                             @RequestParam boolean active,
                             @AuthenticationPrincipal UserPrincipal principal) {
        return userService.setActive(id, active, principal.getId());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public TempPasswordResponse resetPassword(@PathVariable Long id) {
        return userService.resetPassword(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        userService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
