// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.dashboard;

import com.emergencyconnectuae.incident.dto.IncidentResponse;

import java.util.List;
import java.util.Map;

public interface DashboardService {
    /** Cached metrics by status / emirate (SRS 5.4, cached 120 s). */
    Map<String, Object> getSummary();

    /** Live top-N incidents from the Redis priority queue (never cached). */
    List<IncidentResponse> getPriorityQueue(int topN);
}
