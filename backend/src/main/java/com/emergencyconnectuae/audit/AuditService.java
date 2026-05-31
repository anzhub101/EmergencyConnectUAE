// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.audit;

import com.emergencyconnectuae.audit.dto.AuditLogResponse;
import com.emergencyconnectuae.common.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditService {
    PagedResponse<AuditLogResponse> getLogs(Pageable pageable);

    /** Audit trail for a single resource (e.g. an incident's history). */
    PagedResponse<AuditLogResponse> getResourceHistory(String resourceType, UUID resourceId, Pageable pageable);
}
