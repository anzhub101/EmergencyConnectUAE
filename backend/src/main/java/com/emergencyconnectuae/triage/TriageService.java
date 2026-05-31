// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.triage;

import com.emergencyconnectuae.triage.dto.TriageResponse;

public interface TriageService {

    /**
     * Deterministic, rule-based analysis of an incident description (SRS 6.1).
     * Rules are evaluated top-to-bottom; first match wins. No external API/ML.
     */
    TriageResponse analyze(String description);
}
