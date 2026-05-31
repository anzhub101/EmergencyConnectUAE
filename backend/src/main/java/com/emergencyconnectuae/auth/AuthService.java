// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.auth;

import com.emergencyconnectuae.auth.dto.SessionResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {
    SessionResponse createSession(Jwt jwt, String ipAddress);
    void deleteSession(Jwt jwt);
}
