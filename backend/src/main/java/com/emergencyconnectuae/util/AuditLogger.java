// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

@Component
public class AuditLogger {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(String action, String resourceType, UUID resourceId, String result) {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication() != null ? 
                           SecurityContextHolder.getContext().getAuthentication().getName() : null;
        UUID userId = null;
        try {
            if (userIdStr != null && !userIdStr.equals("anonymousUser")) {
                userId = UUID.fromString(userIdStr);
            }
        } catch (IllegalArgumentException ignored) {}

        String ipAddress = null;
        ServletRequestAttributes attribs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attribs != null) {
            ipAddress = attribs.getRequest().getRemoteAddr();
        }

        String sql = "INSERT INTO audit_logs (user_id, action, resource_type, resource_id, ip_address, result) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, userId, action, resourceType, resourceId, ipAddress, result);
    }
}
