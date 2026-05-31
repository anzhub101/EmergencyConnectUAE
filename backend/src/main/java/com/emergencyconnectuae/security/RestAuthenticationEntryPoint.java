// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 handler — missing/expired/invalid JWT. Counts toward the IP blacklist. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final IpBlacklist ipBlacklist;

    public RestAuthenticationEntryPoint(IpBlacklist ipBlacklist) {
        this.ipBlacklist = ipBlacklist;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ipBlacklist.recordDenial(request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required or token invalid\"}");
    }
}
