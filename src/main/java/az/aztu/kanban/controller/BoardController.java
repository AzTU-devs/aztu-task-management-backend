package az.aztu.kanban.controller;

import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.TaskType;
import az.aztu.kanban.dto.ActivityDtos.ActivityDto;
import az.aztu.kanban.dto.BoardDtos.BoardDetail;
import az.aztu.kanban.dto.BoardDtos.BoardRequest;
import az.aztu.kanban.dto.BoardDtos.BoardSummary;
import az.aztu.kanban.dto.BoardDtos.BoardUpdateRequest;
import az.aztu.kanban.dto.BoardDtos.ColumnDto;
import az.aztu.kanban.dto.BoardDtos.ColumnRequest;
import az.aztu.kanban.dto.BoardDtos.KanbanBoard;
import az.aztu.kanban.dto.BoardDtos.MembersRequest;
import az.aztu.kanban.dto.BoardDtos.ReorderColumnsRequest;
import az.aztu.kanban.dto.UserDtos.UserSummary;
import az.aztu.kanban.security.UserPrincipal;
import az.aztu.kanban.service.ActivityService;
import az.aztu.kanban.service.BoardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
@Tag(name = "Boards")
public class BoardController {

    private final BoardService boardService;
    private final ActivityService activityService;

    @GetMapping
    public List<BoardSummary> list(@RequestParam(required = false) Long platformId,
                                   @RequestParam(defaultValue = "false") boolean mine,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return boardService.list(platformId, mine ? principal.getId() : null);
    }

    @GetMapping("/{boardKey}")
    public BoardDetail get(@PathVariable String boardKey) {
        return boardService.detailByKey(boardKey);
    }

    @GetMapping("/{boardKey}/kanban")
    public KanbanBoard kanban(@PathVariable String boardKey,
                              @RequestParam(required = false) Long assigneeId,
                              @RequestParam(required = false) TaskType type,
                              @RequestParam(required = false) Priority priority,
                              @RequestParam(required = false) String search) {
        return boardService.kanban(boardKey, assigneeId, type, priority, search);
    }

    @GetMapping("/{boardKey}/activity")
    public List<ActivityDto> activity(@PathVariable String boardKey,
                                      @RequestParam(defaultValue = "25") int limit) {
        Long boardId = boardService.getEntityByKey(boardKey).getId();
        return activityService.recentForBoard(boardId, Math.min(limit, 100));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardDetail create(@Valid @RequestBody BoardRequest request,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return boardService.create(request, principal.getUser());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BoardDetail update(@PathVariable Long id, @Valid @RequestBody BoardUpdateRequest request) {
        return boardService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boardService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSummary> addMembers(@PathVariable Long id, @Valid @RequestBody MembersRequest request) {
        return boardService.addMembers(id, request.userIds());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        boardService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/columns")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnDto addColumn(@PathVariable Long id, @Valid @RequestBody ColumnRequest request) {
        return boardService.addColumn(id, request);
    }

    @PutMapping("/{id}/columns/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ColumnDto> reorderColumns(@PathVariable Long id,
                                          @Valid @RequestBody ReorderColumnsRequest request) {
        return boardService.reorderColumns(id, request.columnIds());
    }

    @PutMapping("/columns/{columnId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ColumnDto updateColumn(@PathVariable Long columnId, @Valid @RequestBody ColumnRequest request) {
        return boardService.updateColumn(columnId, request);
    }

    @DeleteMapping("/columns/{columnId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteColumn(@PathVariable Long columnId) {
        boardService.deleteColumn(columnId);
        return ResponseEntity.noContent().build();
    }
}
