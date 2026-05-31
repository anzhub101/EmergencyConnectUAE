// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.assignment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AssignmentResponse {
    private UUID assignmentId;
    private UUID incidentId;
    private UUID unitId;
    private String unitStatus;
    private OffsetDateTime assignedAt;
    private OffsetDateTime releasedAt;

    public AssignmentResponse(UUID assignmentId, UUID incidentId, UUID unitId,
                              String unitStatus, OffsetDateTime assignedAt, OffsetDateTime releasedAt) {
        this.assignmentId = assignmentId;
        this.incidentId = incidentId;
        this.unitId = unitId;
        this.unitStatus = unitStatus;
        this.assignedAt = assignedAt;
        this.releasedAt = releasedAt;
    }

    public UUID getAssignmentId() { return assignmentId; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getUnitId() { return unitId; }
    public String getUnitStatus() { return unitStatus; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
}
