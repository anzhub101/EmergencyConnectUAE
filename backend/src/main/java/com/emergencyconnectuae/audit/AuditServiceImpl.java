// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.audit;

import com.emergencyconnectuae.audit.dto.AuditLogResponse;
import com.emergencyconnectuae.common.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Audit logs are never cached (SRS 5.4) — always read directly from Supabase.
 */
@Service
@Transactional(readOnly = true)
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public PagedResponse<AuditLogResponse> getLogs(Pageable pageable) {
        return toPaged(auditLogRepository.findAll(pageable));
    }

    @Override
    public PagedResponse<AuditLogResponse> getResourceHistory(String resourceType, UUID resourceId, Pageable pageable) {
        return toPaged(auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable));
    }

    private PagedResponse<AuditLogResponse> toPaged(Page<AuditLog> page) {
        return new PagedResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }

    private AuditLogResponse toResponse(AuditLog logEntry) {
        return new AuditLogResponse(
                logEntry.getId(),
                logEntry.getUser() != null ? logEntry.getUser().getId() : null,
                logEntry.getUser() != null ? logEntry.getUser().getEmail() : "System",
                logEntry.getAction(),
                logEntry.getResourceType(),
                logEntry.getResourceId(),
                logEntry.getTimestamp(),
                logEntry.getIpAddress(),
                logEntry.getResult());
    }
}
