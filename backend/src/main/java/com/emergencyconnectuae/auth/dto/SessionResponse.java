// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class SessionResponse {
    @Schema(description = "Human-readable session result", example = "Session established")
    private String message;

    public SessionResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
