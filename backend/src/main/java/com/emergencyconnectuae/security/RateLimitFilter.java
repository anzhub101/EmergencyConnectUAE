// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.security;

import com.emergencyconnectuae.util.RedisCacheKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Runs first in the chain (SRS 8.5). Order:
 *   1. reject path-traversal payloads with 400
 *   2. reject blacklisted IPs with 403
 *   3. enforce the 5 attempts/min/IP login limit on /auth/session
 * Per-user (60/min) limiting happens in {@link JwtValidationFilter}, after the
 * JWT has been validated and the authenticated subject is known.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedissonClient redissonClient;
    private final IpBlacklist ipBlacklist;

    public RateLimitFilter(RedissonClient redissonClient, IpBlacklist ipBlacklist) {
        this.redissonClient = redissonClient;
        this.ipBlacklist = ipBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();

        if (hasTraversal(request.getRequestURI()) || hasTraversal(request.getQueryString())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Illegal path");
            return;
        }

        if (ipBlacklist.isBlacklisted(ip)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "IP blacklisted");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/api/v1/auth/session")) {
            RRateLimiter loginLimiter = redissonClient.getRateLimiter(RedisCacheKeys.RATE_LIMIT_LOGIN_PREFIX + ip);
            loginLimiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.MINUTES);
            if (!loginLimiter.tryAcquire()) {
                response.setHeader("Retry-After", "60");
                response.sendError(429, "Too Many Requests");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasTraversal(String value) {
        if (value == null) return false;
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("../") || v.contains("..\\")
                || v.contains("%2e%2e") || v.contains("..%2f")
                || v.contains("\0") || v.contains("%00");
    }
}
