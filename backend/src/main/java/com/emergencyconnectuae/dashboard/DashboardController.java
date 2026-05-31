// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.dashboard;

import com.emergencyconnectuae.incident.dto.IncidentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Cached metrics by emirate / status (cached 120 s)")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/priority")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Live top-N incidents from the Redis priority queue (never cached)")
    public ResponseEntity<List<IncidentResponse>> priority(@RequestParam(defaultValue = "10") int topN) {
        return ResponseEntity.ok(dashboardService.getPriorityQueue(topN));
    }
}
