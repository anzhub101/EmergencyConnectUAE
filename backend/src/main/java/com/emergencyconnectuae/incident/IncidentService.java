// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.incident;

import com.emergencyconnectuae.common.PagedResponse;
import com.emergencyconnectuae.incident.dto.IncidentRequest;
import com.emergencyconnectuae.incident.dto.IncidentResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IncidentService {
    IncidentResponse createIncident(UUID reporterId, IncidentRequest request);
    IncidentResponse getIncident(UUID incidentId);
    PagedResponse<IncidentResponse> listIncidents(String status, Pageable pageable);
    IncidentResponse updateStatus(UUID incidentId, String newStatus);
}
