package az.aztu.kanban.controller;

import az.aztu.kanban.dto.PlatformDtos.PlatformDto;
import az.aztu.kanban.dto.PlatformDtos.PlatformRequest;
import az.aztu.kanban.service.PlatformService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platforms")
@RequiredArgsConstructor
@Tag(name = "Platforms")
public class PlatformController {

    private final PlatformService platformService;

    @GetMapping
    public List<PlatformDto> list(@RequestParam(defaultValue = "false") boolean onlyActive) {
        return platformService.list(onlyActive);
    }

    @GetMapping("/{id}")
    public PlatformDto get(@PathVariable Long id) {
        return platformService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformDto create(@Valid @RequestBody PlatformRequest request) {
        return platformService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlatformDto update(@PathVariable Long id, @Valid @RequestBody PlatformRequest request) {
        return platformService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        platformService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
