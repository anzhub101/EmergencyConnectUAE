// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.health;

import io.swagger.v3.oas.annotations.Operation;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/v1/health — confirms Redis connectivity (Redisson ping) and Supabase
 * reachability (SRS 5.4). Returns 503 if either dependency is unreachable.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final RedissonClient redissonClient;
    private final JdbcTemplate jdbcTemplate;

    public HealthController(RedissonClient redissonClient, JdbcTemplate jdbcTemplate) {
        this.redissonClient = redissonClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @Operation(summary = "System health check (Redis + Supabase)")
    public ResponseEntity<Map<String, Object>> health() {
        boolean redisUp = checkRedis();
        boolean dbUp = checkDatabase();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", redisUp && dbUp ? "UP" : "DOWN");
        body.put("redis", redisUp ? "UP" : "DOWN");
        body.put("supabase", dbUp ? "UP" : "DOWN");

        return (redisUp && dbUp)
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkRedis() {
        try {
            // Lightweight round-trip to confirm Redisson connectivity.
            redissonClient.getBucket("health:ping").isExists();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkDatabase() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(one);
        } catch (Exception e) {
            return false;
        }
    }
}
