// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.security;

import com.emergencyconnectuae.util.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 handler — RBAC/MFA denial from @PreAuthorize. Writes an ACCESS_DENIED
 * audit row (SRS 4) and counts toward the IP blacklist.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final IpBlacklist ipBlacklist;
    private final AuditLogger auditLogger;

    public RestAccessDeniedHandler(IpBlacklist ipBlacklist, AuditLogger auditLogger) {
        this.ipBlacklist = ipBlacklist;
        this.auditLogger = auditLogger;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        ipBlacklist.recordDenial(request.getRemoteAddr());
        try {
            auditLogger.log("ACCESS_DENIED", "SESSION", null, "DENIED");
        } catch (Exception ignored) {
            // never let audit logging break the response
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Insufficient role for this endpoint\"}");
    }
}
