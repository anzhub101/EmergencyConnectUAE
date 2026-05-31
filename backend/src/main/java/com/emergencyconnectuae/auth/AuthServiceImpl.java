// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.auth;

import com.emergencyconnectuae.auth.dto.SessionResponse;
import com.emergencyconnectuae.exception.ResourceNotFoundException;
import com.emergencyconnectuae.util.AuditLogger;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private final RedissonClient redissonClient;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    public AuthServiceImpl(RedissonClient redissonClient, UserRepository userRepository, AuditLogger auditLogger) {
        this.redissonClient = redissonClient;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    @Override
    public SessionResponse createSession(Jwt jwt, String ipAddress) {
        String jti = jwt.getId();
        UUID userId = UUID.fromString(jwt.getSubject());
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        RMapCache<String, Object> sessionCache = redissonClient.getMapCache("session");

        long expirySeconds = jwt.getExpiresAt() != null ? 
                             jwt.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond() : 3600;
        
        if (expirySeconds <= 0) expirySeconds = 1;

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("userId", userId);
        sessionData.put("role", user.getRole());
        sessionData.put("ipAddress", ipAddress);
        sessionData.put("expiresAt", jwt.getExpiresAt() != null ? jwt.getExpiresAt().toEpochMilli() : 0);

        sessionCache.put(jti, sessionData, expirySeconds, TimeUnit.SECONDS);

        auditLogger.log("SESSION_CREATED", "SESSION", userId, "SUCCESS");

        return new SessionResponse("Session created successfully");
    }

    @Override
    public void deleteSession(String jti) {
        RMapCache<String, Object> sessionCache = redissonClient.getMapCache("session");
        sessionCache.remove(jti);
        auditLogger.log("SESSION_DELETED", "SESSION", null, "SUCCESS");
    }
}
