// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.assignment;

import com.emergencyconnectuae.assignment.dto.AssignmentRequest;
import com.emergencyconnectuae.assignment.dto.AssignmentResponse;
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
@RequestMapping("/api/v1/assignments")
@SecurityRequirement(name = "bearerAuth")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DISPATCHER')")
    @Operation(summary = "Assign a unit to an incident (acquires Redisson RLock; 409 on contention)")
    public ResponseEntity<AssignmentResponse> assign(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody AssignmentRequest request) {
        UUID dispatcherId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.assignUnit(dispatcherId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DISPATCHER')")
    @Operation(summary = "Release an assignment and return the unit to AVAILABLE")
    public ResponseEntity<AssignmentResponse> release(@PathVariable UUID id) {
        return ResponseEntity.ok(assignmentService.releaseAssignment(id));
    }
}
