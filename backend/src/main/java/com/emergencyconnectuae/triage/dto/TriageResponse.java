// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.triage.dto;

import java.util.List;

/**
 * Structured AI-triage recommendation (SRS 6.1).
 */
public class TriageResponse {
    private String criticality;
    private double confidence;
    private List<String> recommendedUnits;
    private String recommendedHospitalTier;
    private List<String> matchedKeywords;
    private int dispatchCount;

    public TriageResponse(String criticality, double confidence, List<String> recommendedUnits,
                          String recommendedHospitalTier, List<String> matchedKeywords, int dispatchCount) {
        this.criticality = criticality;
        this.confidence = confidence;
        this.recommendedUnits = recommendedUnits;
        this.recommendedHospitalTier = recommendedHospitalTier;
        this.matchedKeywords = matchedKeywords;
        this.dispatchCount = dispatchCount;
    }

    public String getCriticality() { return criticality; }
    public double getConfidence() { return confidence; }
    public List<String> getRecommendedUnits() { return recommendedUnits; }
    public String getRecommendedHospitalTier() { return recommendedHospitalTier; }
    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public int getDispatchCount() { return dispatchCount; }
}
