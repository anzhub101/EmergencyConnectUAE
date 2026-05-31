// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.triage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TriageRequest {

    @NotBlank
    @Size(max = 2000)
    @Schema(example = "Car accident on Sheikh Zayed Road, 3 vehicles involved")
    private String description;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
