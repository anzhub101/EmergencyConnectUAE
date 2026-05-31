// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource;

import com.emergencyconnectuae.common.PageSupport;
import com.emergencyconnectuae.common.PagedResponse;
import com.emergencyconnectuae.resource.dto.ProximityResponse;
import com.emergencyconnectuae.resource.dto.ResourceAvailabilityRequest;
import com.emergencyconnectuae.resource.dto.ResourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @Operation(summary = "Paginated hospital bed/ICU availability list (Redis-cached)")
    public ResponseEntity<PagedResponse<ResourceResponse>> list(
            @RequestParam(required = false) String emirate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(resourceService.listAvailability(emirate, PageSupport.of(page, size)));
    }

    @GetMapping("/proximity")
    @Operation(summary = "PostGIS nearest available units (ST_DWithin/ST_Distance; never cached)")
    public ResponseEntity<List<ProximityResponse>> proximity(
            @RequestParam @Min(-90) @Max(90) double lat,
            @RequestParam @Min(-180) @Max(180) double lng,
            @RequestParam(defaultValue = "10000") @Min(1) double radius,
            @RequestParam(defaultValue = "ALL") String type) {
        return ResponseEntity.ok(resourceService.proximity(lat, lng, radius, type));
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Single hospital availability (Redis-cached)")
    public ResponseEntity<ResourceResponse> availability(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceService.getAvailability(id));
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Update hospital bed/ICU counts (Hospital Admin); evicts cache")
    public ResponseEntity<ResourceResponse> updateAvailability(
            @PathVariable UUID id, @Valid @RequestBody ResourceAvailabilityRequest request) {
        return ResponseEntity.ok(resourceService.updateAvailability(id, request));
    }

    @PutMapping("/{id}/reserve")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'SYSTEM_ADMIN')")
    @Operation(summary = "Reserve a bed (acquires Redisson RLock; 409 on contention)")
    public ResponseEntity<ResourceResponse> reserve(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceService.reserveBed(id));
    }
}
