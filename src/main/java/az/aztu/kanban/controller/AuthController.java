package az.aztu.kanban.controller;

import az.aztu.kanban.dto.AuthDtos.ChangePasswordRequest;
import az.aztu.kanban.dto.AuthDtos.LoginRequest;
import az.aztu.kanban.dto.AuthDtos.LoginResponse;
import az.aztu.kanban.dto.UserDtos.UpdateProfileRequest;
import az.aztu.kanban.dto.UserDtos.UserDto;
import az.aztu.kanban.security.UserPrincipal;
import az.aztu.kanban.service.AuthService;
import az.aztu.kanban.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.get(principal.getId());
    }

    @PutMapping("/me")
    public UserDto updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.getId(), request);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
