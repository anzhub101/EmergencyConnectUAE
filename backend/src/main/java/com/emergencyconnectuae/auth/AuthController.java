// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.auth;

import com.emergencyconnectuae.auth.dto.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirement(name = "bearerAuth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/session")
    @Operation(summary = "Open a server session for a valid Supabase JWT (stored in Redis)")
    public ResponseEntity<SessionResponse> createSession(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok(authService.createSession(jwt, request.getRemoteAddr()));
    }

    @DeleteMapping("/session")
    @Operation(summary = "Revoke the current session (logout); the JWT is rejected afterwards")
    public ResponseEntity<Void> deleteSession(@AuthenticationPrincipal Jwt jwt) {
        authService.deleteSession(jwt.getId());
        return ResponseEntity.noContent().build();
    }
}
