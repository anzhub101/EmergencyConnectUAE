// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.triage;

import com.emergencyconnectuae.triage.dto.TriageRequest;
import com.emergencyconnectuae.triage.dto.TriageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/triage")
@SecurityRequirement(name = "bearerAuth")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Rule-based AI triage of an incident description (SRS 6.1)")
    public ResponseEntity<TriageResponse> analyze(@Valid @RequestBody TriageRequest request) {
        return ResponseEntity.ok(triageService.analyze(request.getDescription()));
    }
}
