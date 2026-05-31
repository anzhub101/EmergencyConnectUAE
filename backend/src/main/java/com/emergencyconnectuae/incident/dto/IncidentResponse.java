// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.incident.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public class IncidentResponse implements Serializable {
    private UUID id;
    private String description;
    private String status;
    private String criticality;
    private Double latitude;
    private Double longitude;
    private UUID reportedBy;
    private Double priorityScore;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public IncidentResponse(UUID id, String description, String status, String criticality,
                            Double latitude, Double longitude, UUID reportedBy, Double priorityScore,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.description = description;
        this.status = status;
        this.criticality = criticality;
        this.latitude = latitude;
        this.longitude = longitude;
        this.reportedBy = reportedBy;
        this.priorityScore = priorityScore;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getCriticality() { return criticality; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public UUID getReportedBy() { return reportedBy; }
    public Double getPriorityScore() { return priorityScore; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
