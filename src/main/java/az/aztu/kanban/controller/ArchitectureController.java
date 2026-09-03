package az.aztu.kanban.controller;

import az.aztu.kanban.dto.ArchitectureDtos.DiagramRequest;
import az.aztu.kanban.dto.ArchitectureDtos.DiagramSummary;
import az.aztu.kanban.dto.ArchitectureDtos.DiagramView;
import az.aztu.kanban.dto.ArchitectureDtos.EdgeDto;
import az.aztu.kanban.dto.ArchitectureDtos.EdgeRequest;
import az.aztu.kanban.dto.ArchitectureDtos.NodeDto;
import az.aztu.kanban.dto.ArchitectureDtos.NodePositionRequest;
import az.aztu.kanban.dto.ArchitectureDtos.NodeRequest;
import az.aztu.kanban.security.UserPrincipal;
import az.aztu.kanban.service.ArchitectureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Diagrams sit under /diagrams rather than at the root of /architecture so that
 * /architecture/nodes/{id} and /architecture/edges/{id} can never be mistaken for a diagram id.
 */
@RestController
@RequestMapping("/api/architecture")
@RequiredArgsConstructor
@Tag(name = "Architecture")
public class ArchitectureController {

    private final ArchitectureService architectureService;

    @GetMapping("/diagrams")
    public List<DiagramSummary> list(@RequestParam(required = false) Long platformId) {
        return architectureService.list(platformId);
    }

    @GetMapping("/diagrams/{id}")
    public DiagramView get(@PathVariable Long id) {
        return architectureService.view(id);
    }

    @PostMapping("/diagrams")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DiagramSummary create(@Valid @RequestBody DiagramRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return architectureService.create(request, principal.getUser());
    }

    @PutMapping("/diagrams/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DiagramSummary update(@PathVariable Long id, @Valid @RequestBody DiagramRequest request) {
        return architectureService.update(id, request);
    }

    @DeleteMapping("/diagrams/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        architectureService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- nodes

    @PostMapping("/diagrams/{id}/nodes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public NodeDto addNode(@PathVariable Long id,
                           @Valid @RequestBody NodeRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return architectureService.addNode(id, request, principal.getUser());
    }

    @PutMapping("/nodes/{nodeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public NodeDto updateNode(@PathVariable Long nodeId, @Valid @RequestBody NodeRequest request) {
        return architectureService.updateNode(nodeId, request);
    }

    @PatchMapping("/nodes/{nodeId}/position")
    @PreAuthorize("hasRole('ADMIN')")
    public NodeDto moveNode(@PathVariable Long nodeId, @Valid @RequestBody NodePositionRequest request) {
        return architectureService.moveNode(nodeId, request);
    }

    @DeleteMapping("/nodes/{nodeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNode(@PathVariable Long nodeId) {
        architectureService.deleteNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- edges

    @PostMapping("/diagrams/{id}/edges")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EdgeDto addEdge(@PathVariable Long id, @Valid @RequestBody EdgeRequest request) {
        return architectureService.addEdge(id, request);
    }

    @PutMapping("/edges/{edgeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public EdgeDto updateEdge(@PathVariable Long edgeId, @Valid @RequestBody EdgeRequest request) {
        return architectureService.updateEdge(edgeId, request);
    }

    @DeleteMapping("/edges/{edgeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEdge(@PathVariable Long edgeId) {
        architectureService.deleteEdge(edgeId);
        return ResponseEntity.noContent().build();
    }
}
