// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AssignmentRequest {

    @NotNull
    @Schema(description = "Incident to dispatch a unit to",
            example = "33333333-3333-3333-3333-333333333301")
    private UUID incidentId;

    @NotNull
    @Schema(description = "Emergency unit to assign (must be AVAILABLE; row-locked during assign)",
            example = "22222222-2222-2222-2222-222222222201")
    private UUID unitId;

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }
    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }
}
