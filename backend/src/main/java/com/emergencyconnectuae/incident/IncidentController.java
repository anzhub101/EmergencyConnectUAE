// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.incident;

import com.emergencyconnectuae.audit.AuditService;
import com.emergencyconnectuae.audit.dto.AuditLogResponse;
import com.emergencyconnectuae.common.PageSupport;
import com.emergencyconnectuae.common.PagedResponse;
import com.emergencyconnectuae.incident.dto.IncidentRequest;
import com.emergencyconnectuae.incident.dto.IncidentResponse;
import com.emergencyconnectuae.incident.dto.UpdateStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final IncidentService incidentService;
    private final AuditService auditService;

    public IncidentController(IncidentService incidentService, AuditService auditService) {
        this.incidentService = incidentService;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DISPATCHER')")
    @Operation(summary = "Create an incident from a 999 call (runs AI triage)")
    public ResponseEntity<IncidentResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody IncidentRequest request) {
        UUID reporterId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentService.createIncident(reporterId, request));
    }

    @GetMapping
    @Operation(summary = "Paginated incident list, ordered by priority; filterable by status")
    public ResponseEntity<PagedResponse<IncidentResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(incidentService.listIncidents(status, PageSupport.of(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Incident detail (Redis-cached)")
    public ResponseEntity<IncidentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'RESPONDER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Transition status OPEN -> IN_PROGRESS -> RESOLVED (422 on illegal transition)")
    public ResponseEntity<IncidentResponse> updateStatus(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(incidentService.updateStatus(id, request.getStatus()));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Paginated audit trail for an incident (never cached)")
    public ResponseEntity<PagedResponse<AuditLogResponse>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getResourceHistory("INCIDENT", id, PageSupport.of(page, size)));
    }
}
