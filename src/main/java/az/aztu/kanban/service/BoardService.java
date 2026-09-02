package az.aztu.kanban.service;

import az.aztu.kanban.domain.Board;
import az.aztu.kanban.domain.BoardColumn;
import az.aztu.kanban.domain.ColumnCategory;
import az.aztu.kanban.domain.NotificationType;
import az.aztu.kanban.domain.Platform;
import az.aztu.kanban.domain.Task;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.BoardDtos.BoardDetail;
import az.aztu.kanban.dto.BoardDtos.BoardRequest;
import az.aztu.kanban.dto.BoardDtos.BoardSummary;
import az.aztu.kanban.dto.BoardDtos.BoardUpdateRequest;
import az.aztu.kanban.dto.BoardDtos.ColumnDto;
import az.aztu.kanban.dto.BoardDtos.ColumnRequest;
import az.aztu.kanban.dto.BoardDtos.KanbanBoard;
import az.aztu.kanban.dto.BoardDtos.KanbanColumn;
import az.aztu.kanban.dto.PlatformDtos.PlatformDto;
import az.aztu.kanban.dto.TaskDtos.TaskCard;
import az.aztu.kanban.dto.UserDtos.UserSummary;
import az.aztu.kanban.exception.BadRequestException;
import az.aztu.kanban.exception.ConflictException;
import az.aztu.kanban.exception.NotFoundException;
import az.aztu.kanban.repository.BoardColumnRepository;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.ActivityRepository;
import az.aztu.kanban.repository.TaskCommentRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import az.aztu.kanban.domain.Priority;
import az.aztu.kanban.domain.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BoardService {

    private static final List<ColumnRequest> DEFAULT_COLUMNS = List.of(
            new ColumnRequest("Backlog", null, ColumnCategory.TODO),
            new ColumnRequest("To Do", null, ColumnCategory.TODO),
            new ColumnRequest("In Progress", 5, ColumnCategory.IN_PROGRESS),
            new ColumnRequest("In Review", 3, ColumnCategory.IN_PROGRESS),
            new ColumnRequest("Done", null, ColumnCategory.DONE));

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final PlatformRepository platformRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final ActivityRepository activityRepository;
    private final NotificationService notificationService;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<BoardSummary> list(Long platformId, Long onlyForUserId) {
        List<Board> boards;
        if (onlyForUserId != null) {
            boards = boardRepository.findBoardsForUser(onlyForUserId);
            if (platformId != null) {
                boards = boards.stream()
                        .filter(b -> b.getPlatform() != null && b.getPlatform().getId().equals(platformId))
                        .toList();
            }
        } else if (platformId != null) {
            boards = boardRepository.findAllByPlatformIdOrderByNameAsc(platformId);
        } else {
            boards = boardRepository.findAllByOrderByNameAsc();
        }
        return boards.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public BoardDetail detailByKey(String boardKey) {
        return toDetail(getEntityByKey(boardKey));
    }

    @Transactional(readOnly = true)
    public BoardDetail detail(Long id) {
        return toDetail(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Board getEntity(Long id) {
        return boardRepository.findById(id).orElseThrow(() -> NotFoundException.of("Board", id));
    }

    @Transactional(readOnly = true)
    public Board getEntityByKey(String boardKey) {
        return boardRepository.findByBoardKeyIgnoreCase(boardKey)
                .orElseThrow(() -> NotFoundException.of("Board", boardKey));
    }

    @Transactional(readOnly = true)
    public KanbanBoard kanban(String boardKey, Long assigneeId, TaskType type, Priority priority, String search) {
        Board board = getEntityByKey(boardKey);
        List<BoardColumn> columns = columnRepository.findAllByBoardIdOrderByPositionAsc(board.getId());
        List<Task> tasks = taskRepository.findForBoard(
                board.getId(), assigneeId, type, priority,
                (search == null || search.isBlank()) ? null : search.trim());

        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : commentRepository.countsByBoard(board.getId())) {
            commentCounts.put((Long) row[0], (Long) row[1]);
        }

        Map<Long, List<TaskCard>> byColumn = new HashMap<>();
        for (Task task : tasks) {
            byColumn.computeIfAbsent(task.getBoardColumn().getId(), key -> new ArrayList<>())
                    .add(TaskCard.from(task, commentCounts.getOrDefault(task.getId(), 0L)));
        }

        List<KanbanColumn> kanbanColumns = columns.stream()
                .map(column -> {
                    List<TaskCard> cards = byColumn.getOrDefault(column.getId(), new ArrayList<>());
                    cards.sort(Comparator.comparingInt(TaskCard::orderIndex));
                    return new KanbanColumn(
                            column.getId(),
                            column.getName(),
                            column.getPosition(),
                            column.getWipLimit(),
                            column.getCategory(),
                            cards);
                })
                .toList();

        return new KanbanBoard(toDetail(board), kanbanColumns);
    }

    // ---------------------------------------------------------------- mutations

    @Transactional
    public BoardDetail create(BoardRequest request, User creator) {
        String key = request.boardKey().trim().toUpperCase();
        if (boardRepository.existsByBoardKeyIgnoreCase(key)) {
            throw new ConflictException("A board with key " + key + " already exists.");
        }
        Platform platform = platformRepository.findById(request.platformId())
                .orElseThrow(() -> NotFoundException.of("Platform", request.platformId()));

        Board board = new Board();
        board.setName(request.name().trim());
        board.setBoardKey(key);
        board.setDescription(request.description());
        board.setPlatform(platform);
        if (request.color() != null && !request.color().isBlank()) {
            board.setColor(request.color());
        }
        if (request.leadId() != null) {
            board.setLead(userRepository.findById(request.leadId())
                    .orElseThrow(() -> NotFoundException.of("User", request.leadId())));
        }

        Set<User> members = new LinkedHashSet<>();
        if (request.memberIds() != null) {
            for (Long userId : request.memberIds()) {
                members.add(userRepository.findById(userId)
                        .orElseThrow(() -> NotFoundException.of("User", userId)));
            }
        }
        if (board.getLead() != null) {
            members.add(board.getLead());
        }
        if (creator != null) {
            members.add(creator);
        }
        board.setMembers(members);

        List<ColumnRequest> columnRequests =
                (request.columns() == null || request.columns().isEmpty()) ? DEFAULT_COLUMNS : request.columns();
        int position = 0;
        for (ColumnRequest columnRequest : columnRequests) {
            BoardColumn column = new BoardColumn();
            column.setName(columnRequest.name().trim());
            column.setCategory(columnRequest.category());
            column.setWipLimit(normalizeWip(columnRequest.wipLimit()));
            column.setPosition(position++);
            column.setBoard(board);
            board.getColumns().add(column);
        }

        boardRepository.save(board);

        for (User member : members) {
            if (creator == null || !member.getId().equals(creator.getId())) {
                notificationService.push(member, NotificationType.BOARD_INVITE,
                        "You were added to " + board.getName(),
                        "You now have access to the " + board.getName() + " board.",
                        "/boards/" + board.getBoardKey());
            }
        }

        return toDetail(board);
    }

    @Transactional
    public BoardDetail update(Long id, BoardUpdateRequest request) {
        Board board = getEntity(id);
        Platform platform = platformRepository.findById(request.platformId())
                .orElseThrow(() -> NotFoundException.of("Platform", request.platformId()));
        board.setName(request.name().trim());
        board.setDescription(request.description());
        board.setPlatform(platform);
        if (request.color() != null && !request.color().isBlank()) {
            board.setColor(request.color());
        }
        if (request.leadId() != null) {
            User lead = userRepository.findById(request.leadId())
                    .orElseThrow(() -> NotFoundException.of("User", request.leadId()));
            board.setLead(lead);
            board.getMembers().add(lead);
        } else {
            board.setLead(null);
        }
        if (request.archived() != null) {
            board.setArchived(request.archived());
        }
        return toDetail(boardRepository.save(board));
    }

    @Transactional
    public void delete(Long id) {
        Board board = getEntity(id);

        // Remove everything that points at this board before the board itself.
        activityRepository.deleteAllByBoardId(id);
        List<Task> tasks = taskRepository.findAllByBoardId(id);
        for (Task task : tasks) {
            commentRepository.deleteAllByTaskId(task.getId());
            task.getWatchers().clear();
            task.getLabels().clear();
        }
        taskRepository.saveAll(tasks);
        taskRepository.deleteAll(tasks);

        board.getMembers().clear();
        boardRepository.save(board);
        boardRepository.delete(board);
    }

    @Transactional
    public List<UserSummary> addMembers(Long boardId, List<Long> userIds) {
        Board board = getEntity(boardId);
        for (Long userId : userIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> NotFoundException.of("User", userId));
            if (board.getMembers().add(user)) {
                notificationService.push(user, NotificationType.BOARD_INVITE,
                        "You were added to " + board.getName(),
                        "You now have access to the " + board.getName() + " board.",
                        "/boards/" + board.getBoardKey());
            }
        }
        boardRepository.save(board);
        return board.getMembers().stream().map(UserSummary::from).toList();
    }

    @Transactional
    public void removeMember(Long boardId, Long userId) {
        Board board = getEntity(boardId);
        if (board.getLead() != null && board.getLead().getId().equals(userId)) {
            throw new BadRequestException("The board lead cannot be removed. Change the lead first.");
        }
        board.getMembers().removeIf(member -> member.getId().equals(userId));
        boardRepository.save(board);
    }

    // ---------------------------------------------------------------- columns

    @Transactional
    public ColumnDto addColumn(Long boardId, ColumnRequest request) {
        Board board = getEntity(boardId);
        long count = columnRepository.countByBoardId(boardId);
        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setName(request.name().trim());
        column.setCategory(request.category());
        column.setWipLimit(normalizeWip(request.wipLimit()));
        column.setPosition((int) count);
        columnRepository.save(column);
        return ColumnDto.from(column, 0);
    }

    @Transactional
    public ColumnDto updateColumn(Long columnId, ColumnRequest request) {
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> NotFoundException.of("Column", columnId));
        column.setName(request.name().trim());
        column.setCategory(request.category());
        column.setWipLimit(normalizeWip(request.wipLimit()));
        columnRepository.save(column);
        return ColumnDto.from(column, taskRepository.countByBoardColumnId(columnId));
    }

    @Transactional
    public void deleteColumn(Long columnId) {
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> NotFoundException.of("Column", columnId));
        if (taskRepository.countByBoardColumnId(columnId) > 0) {
            throw new BadRequestException("Move the tasks out of this column before deleting it.");
        }
        Long boardId = column.getBoard().getId();
        if (columnRepository.countByBoardId(boardId) <= 1) {
            throw new BadRequestException("A board must keep at least one column.");
        }
        columnRepository.delete(column);

        List<BoardColumn> remaining = columnRepository.findAllByBoardIdOrderByPositionAsc(boardId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        columnRepository.saveAll(remaining);
    }

    @Transactional
    public List<ColumnDto> reorderColumns(Long boardId, List<Long> columnIds) {
        List<BoardColumn> columns = columnRepository.findAllByBoardIdOrderByPositionAsc(boardId);
        Map<Long, BoardColumn> byId = new HashMap<>();
        columns.forEach(column -> byId.put(column.getId(), column));

        int position = 0;
        for (Long columnId : columnIds) {
            BoardColumn column = byId.get(columnId);
            if (column == null) {
                throw new BadRequestException("Column " + columnId + " does not belong to this board.");
            }
            column.setPosition(position++);
        }
        columnRepository.saveAll(columns);
        return columnRepository.findAllByBoardIdOrderByPositionAsc(boardId).stream()
                .map(column -> ColumnDto.from(column, taskRepository.countByBoardColumnId(column.getId())))
                .toList();
    }

    // ---------------------------------------------------------------- mapping

    private Integer normalizeWip(Integer wipLimit) {
        return (wipLimit == null || wipLimit <= 0) ? null : wipLimit;
    }

    private BoardSummary toSummary(Board board) {
        long total = taskRepository.countByBoardId(board.getId());
        long done = taskRepository.findAllByBoardId(board.getId()).stream()
                .filter(task -> task.getBoardColumn().getCategory() == ColumnCategory.DONE)
                .count();
        return az.aztu.kanban.dto.BoardDtos.summary(board, total, done);
    }

    private BoardDetail toDetail(Board board) {
        Platform platform = board.getPlatform();
        PlatformDto platformDto = platform == null ? null
                : PlatformDto.from(platform, boardRepository.countByPlatformId(platform.getId()), 0);

        List<ColumnDto> columns = columnRepository.findAllByBoardIdOrderByPositionAsc(board.getId()).stream()
                .map(column -> ColumnDto.from(column, taskRepository.countByBoardColumnId(column.getId())))
                .toList();

        List<UserSummary> members = board.getMembers().stream()
                .map(UserSummary::from)
                .sorted(Comparator.comparing(UserSummary::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new BoardDetail(
                board.getId(),
                board.getName(),
                board.getBoardKey(),
                board.getDescription(),
                board.getColor(),
                platformDto,
                UserSummary.from(board.getLead()),
                members,
                columns,
                taskRepository.countByBoardId(board.getId()),
                board.isArchived(),
                board.getCreatedAt());
    }
}
