package az.aztu.kanban.dto;

import az.aztu.kanban.domain.Board;
import az.aztu.kanban.domain.BoardColumn;
import az.aztu.kanban.domain.ColumnCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class BoardDtos {

    private BoardDtos() {
    }

    public record ColumnDto(
            Long id,
            String name,
            int position,
            Integer wipLimit,
            ColumnCategory category,
            long taskCount
    ) {
        public static ColumnDto from(BoardColumn column, long taskCount) {
            return new ColumnDto(
                    column.getId(),
                    column.getName(),
                    column.getPosition(),
                    column.getWipLimit(),
                    column.getCategory(),
                    taskCount);
        }
    }

    public record BoardSummary(
            Long id,
            String name,
            String boardKey,
            String description,
            String color,
            Long platformId,
            String platformName,
            String platformColor,
            UserDtos.UserSummary lead,
            int memberCount,
            long taskCount,
            long doneCount,
            boolean archived,
            Instant createdAt
    ) {
    }

    public record BoardDetail(
            Long id,
            String name,
            String boardKey,
            String description,
            String color,
            PlatformDtos.PlatformDto platform,
            UserDtos.UserSummary lead,
            List<UserDtos.UserSummary> members,
            List<ColumnDto> columns,
            long taskCount,
            boolean archived,
            Instant createdAt
    ) {
    }

    public record BoardRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(min = 2, max = 10)
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]*$", message = "Board key must start with a letter and contain only letters and digits")
            String boardKey,
            @Size(max = 1000) String description,
            @Size(max = 20) String color,
            @NotNull Long platformId,
            Long leadId,
            List<Long> memberIds,
            List<ColumnRequest> columns
    ) {
    }

    public record BoardUpdateRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Size(max = 20) String color,
            @NotNull Long platformId,
            Long leadId,
            Boolean archived
    ) {
    }

    public record ColumnRequest(
            @NotBlank @Size(max = 80) String name,
            Integer wipLimit,
            @NotNull ColumnCategory category
    ) {
    }

    public record MembersRequest(@NotNull List<Long> userIds) {
    }

    public record ReorderColumnsRequest(@NotNull List<Long> columnIds) {
    }

    public record KanbanColumn(
            Long id,
            String name,
            int position,
            Integer wipLimit,
            ColumnCategory category,
            List<TaskDtos.TaskCard> tasks
    ) {
    }

    public record KanbanBoard(
            BoardDetail board,
            List<KanbanColumn> columns
    ) {
    }

    public static BoardSummary summary(Board board, long taskCount, long doneCount) {
        return new BoardSummary(
                board.getId(),
                board.getName(),
                board.getBoardKey(),
                board.getDescription(),
                board.getColor(),
                board.getPlatform() != null ? board.getPlatform().getId() : null,
                board.getPlatform() != null ? board.getPlatform().getName() : null,
                board.getPlatform() != null ? board.getPlatform().getColor() : null,
                UserDtos.UserSummary.from(board.getLead()),
                board.getMembers().size(),
                taskCount,
                doneCount,
                board.isArchived(),
                board.getCreatedAt());
    }
}
