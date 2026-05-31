// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.security;

import org.redisson.api.RMapCache;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.emergencyconnectuae.util.RedisCacheKeys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runs after Spring's BearerTokenAuthenticationFilter has cryptographically
 * validated the JWT via JWKS (SRS 8.5 steps 5-6). Here we:
 *   - enforce the per-user 60/min rate limit (subject now known)
 *   - require a live Redis session (token revocation on logout) -> 401 if absent
 *   - enforce MFA (amr=totp) for Dispatcher / System Admin -> 403 if absent
 *   - re-bind the authentication with the role taken from the session
 * 401/403 outcomes are counted toward the IP auto-blacklist.
 */
@Component
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final String SESSION_MAP = "session";

    private final RedissonClient redissonClient;
    private final IpBlacklist ipBlacklist;

    public JwtValidationFilter(RedissonClient redissonClient, IpBlacklist ipBlacklist) {
        this.redissonClient = redissonClient;
        this.ipBlacklist = ipBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String jti = jwt.getId();
            String ip = request.getRemoteAddr();

            boolean isCreatingSession = "POST".equalsIgnoreCase(request.getMethod())
                    && request.getRequestURI().equals("/api/v1/auth/session");

            // Per-user rate limit: 60 requests / minute (SRS 8.4).
            RRateLimiter userLimiter = redissonClient.getRateLimiter(RedisCacheKeys.RATE_LIMIT_PREFIX + jwt.getSubject());
            userLimiter.trySetRate(RateType.OVERALL, 60, 1, RateIntervalUnit.MINUTES);
            if (!userLimiter.tryAcquire()) {
                response.setHeader("Retry-After", "60");
                response.sendError(429, "Too Many Requests");
                return;
            }

            if (!isCreatingSession) {
                RMapCache<String, Map<String, Object>> sessionCache = redissonClient.getMapCache(SESSION_MAP);
                Map<String, Object> sessionData = sessionCache.get(jti);

                if (sessionData == null) {
                    ipBlacklist.recordDenial(ip);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked or session expired");
                    return;
                }

                String role = (String) sessionData.get("role");

                if ("ROLE_DISPATCHER".equals(role) || "ROLE_SYSTEM_ADMIN".equals(role)) {
                    List<Map<String, Object>> amr = jwt.getClaim("amr");
                    boolean mfaDone = amr != null && amr.stream().anyMatch(m -> "totp".equals(m.get("method")));
                    if (!mfaDone) {
                        ipBlacklist.recordDenial(ip);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "MFA required");
                        return;
                    }
                }

                JwtAuthenticationToken reauth = new JwtAuthenticationToken(
                        jwt, Collections.singletonList(new SimpleGrantedAuthority(role)), jwt.getSubject());
                SecurityContextHolder.getContext().setAuthentication(reauth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
