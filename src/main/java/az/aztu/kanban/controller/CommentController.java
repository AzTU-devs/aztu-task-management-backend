package az.aztu.kanban.controller;

import az.aztu.kanban.dto.CommentDtos.CommentDto;
import az.aztu.kanban.dto.CommentDtos.CommentRequest;
import az.aztu.kanban.security.UserPrincipal;
import az.aztu.kanban.service.CommentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "Comments")
public class CommentController {

    private final CommentService commentService;

    @PutMapping("/{id}")
    public CommentDto update(@PathVariable Long id,
                             @Valid @RequestBody CommentRequest request,
                             @AuthenticationPrincipal UserPrincipal principal) {
        return commentService.update(id, request, principal.getUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        commentService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
