package az.aztu.kanban.controller;

import az.aztu.kanban.dto.ActivityDtos.ActivityDto;
import az.aztu.kanban.dto.DashboardDtos.DashboardStats;
import az.aztu.kanban.security.UserPrincipal;
import az.aztu.kanban.service.ActivityService;
import az.aztu.kanban.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ActivityService activityService;

    @GetMapping("/stats")
    public DashboardStats stats(@AuthenticationPrincipal UserPrincipal principal) {
        return dashboardService.stats(principal.getUser());
    }

    @GetMapping("/activity")
    public List<ActivityDto> activity(@RequestParam(defaultValue = "20") int limit) {
        return activityService.recent(Math.min(limit, 100));
    }
}
