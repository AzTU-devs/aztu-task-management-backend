package az.aztu.kanban.service;

import az.aztu.kanban.domain.Platform;
import az.aztu.kanban.domain.User;
import az.aztu.kanban.dto.PlatformDtos.PlatformDto;
import az.aztu.kanban.dto.PlatformDtos.PlatformRequest;
import az.aztu.kanban.exception.BadRequestException;
import az.aztu.kanban.exception.ConflictException;
import az.aztu.kanban.exception.NotFoundException;
import az.aztu.kanban.repository.ArchitectureDiagramRepository;
import az.aztu.kanban.repository.BoardRepository;
import az.aztu.kanban.repository.PlatformRepository;
import az.aztu.kanban.repository.TaskRepository;
import az.aztu.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformService {

    private final PlatformRepository platformRepository;
    private final BoardRepository boardRepository;
    private final ArchitectureDiagramRepository diagramRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PlatformDto> list(boolean onlyActive) {
        List<Platform> platforms = onlyActive
                ? platformRepository.findAllByActiveTrueOrderByNameAsc()
                : platformRepository.findAllByOrderByNameAsc();
        return platforms.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PlatformDto get(Long id) {
        return toDto(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Platform getEntity(Long id) {
        return platformRepository.findById(id).orElseThrow(() -> NotFoundException.of("Platform", id));
    }

    @Transactional
    public PlatformDto create(PlatformRequest request) {
        String code = request.code().trim().toUpperCase();
        if (platformRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("A platform with code " + code + " already exists.");
        }
        Platform platform = new Platform();
        apply(platform, request, code);
        return toDto(platformRepository.save(platform));
    }

    @Transactional
    public PlatformDto update(Long id, PlatformRequest request) {
        Platform platform = getEntity(id);
        String code = request.code().trim().toUpperCase();
        if (!platform.getCode().equalsIgnoreCase(code) && platformRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("A platform with code " + code + " already exists.");
        }
        apply(platform, request, code);
        return toDto(platformRepository.save(platform));
    }

    @Transactional
    public void delete(Long id) {
        Platform platform = getEntity(id);
        long boards = boardRepository.countByPlatformId(id);
        if (boards > 0) {
            throw new BadRequestException(
                    "This platform still holds " + boards + " board(s). Delete or move them first.");
        }
        // Diagrams point at the platform with a foreign key and there is no cascade for them,
        // so without this guard the delete surfaces as a raw constraint violation and a 500.
        long diagrams = diagramRepository.countByPlatformId(id);
        if (diagrams > 0) {
            throw new BadRequestException(
                    "This platform still holds " + diagrams + " architecture diagram(s). Delete or move them first.");
        }
        platformRepository.delete(platform);
    }

    private void apply(Platform platform, PlatformRequest request, String code) {
        platform.setName(request.name().trim());
        platform.setCode(code);
        platform.setDescription(request.description());
        if (request.color() != null && !request.color().isBlank()) {
            platform.setColor(request.color());
        }
        if (request.icon() != null && !request.icon().isBlank()) {
            platform.setIcon(request.icon());
        }
        if (request.active() != null) {
            platform.setActive(request.active());
        }
        if (request.ownerId() != null) {
            User owner = userRepository.findById(request.ownerId())
                    .orElseThrow(() -> NotFoundException.of("User", request.ownerId()));
            platform.setOwner(owner);
        } else {
            platform.setOwner(null);
        }
    }

    private PlatformDto toDto(Platform platform) {
        long boardCount = boardRepository.countByPlatformId(platform.getId());
        long taskCount = boardRepository.findAllByPlatformIdOrderByNameAsc(platform.getId()).stream()
                .mapToLong(board -> taskRepository.countByBoardId(board.getId()))
                .sum();
        return PlatformDto.from(platform, boardCount, taskCount);
    }
}
