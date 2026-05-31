// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateStatusRequest {

    @NotBlank
    @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED",
             message = "status must be one of OPEN, IN_PROGRESS, RESOLVED")
    @Schema(example = "IN_PROGRESS", allowableValues = {"OPEN", "IN_PROGRESS", "RESOLVED"})
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
