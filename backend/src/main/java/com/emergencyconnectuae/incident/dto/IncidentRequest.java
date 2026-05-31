// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IncidentRequest {

    @NotBlank
    @Size(max = 2000)
    @Schema(description = "Free-text 999-call description; drives AI triage",
            example = "Car accident on Sheikh Zayed Road, 3 vehicles involved")
    private String description;

    @NotNull
    @Schema(example = "25.2110")
    private Double latitude;

    @NotNull
    @Schema(example = "55.2744")
    private Double longitude;

    @Schema(description = "Optional dispatcher override of the AI-derived criticality",
            example = "HIGH", allowableValues = {"CRITICAL", "HIGH", "MEDIUM", "LOW"})
    private String criticality;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) { this.criticality = criticality; }
}
