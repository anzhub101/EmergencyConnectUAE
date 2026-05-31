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
        String sessionKey = sessionKey(jwt);
        UUID userId = UUID.fromString(jwt.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        RMapCache<String, Object> sessionCache = redissonClient.getMapCache("session");

        long expirySeconds = 3600;
        if (jwt.getExpiresAt() != null) {
            if (jwt.getIssuedAt() != null) {
                // Compute absolute token lifetime to remain immune to clock skew
                expirySeconds = jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond();
            } else {
                expirySeconds = jwt.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            }
        }
        
        // Enforce a safe minimum TTL of 1 hour to prevent tiny TTLs due to system clock mismatches
        if (expirySeconds < 3600) {
            expirySeconds = 3600;
        }

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("userId", userId);
        sessionData.put("role", user.getRole());
        sessionData.put("ipAddress", ipAddress);
        sessionData.put("expiresAt", jwt.getExpiresAt() != null ? jwt.getExpiresAt().toEpochMilli() : 0);

        sessionCache.put(sessionKey, sessionData, expirySeconds, TimeUnit.SECONDS);

        auditLogger.log("SESSION_CREATED", "SESSION", userId, "SUCCESS");

        return new SessionResponse("Session created successfully");
    }

    @Override
    public void deleteSession(Jwt jwt) {
        RMapCache<String, Object> sessionCache = redissonClient.getMapCache("session");
        sessionCache.remove(sessionKey(jwt));
        auditLogger.log("SESSION_DELETED", "SESSION", null, "SUCCESS");
    }

    // Supabase access tokens carry a stable `session_id` claim (constant across
    // token refreshes) but no `jti`. We key the Redis session by `session_id`,
    // falling back to the subject so a token without it still resolves.
    static String sessionKey(Jwt jwt) {
        String sid = jwt.getClaimAsString("session_id");
        return sid != null ? sid : jwt.getSubject();
    }
}
