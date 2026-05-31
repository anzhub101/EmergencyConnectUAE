// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.incident;

import com.emergencyconnectuae.util.RedisCacheKeys;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Redis priority dispatch queue (SRS 6.3).
 * Key: incident:priority:queue (RScoredSortedSet).
 * Score = criticality weight x incident age in seconds, so older high-criticality
 * incidents automatically rise above newer lower-priority ones.
 */
@Component
public class PriorityDispatchQueue {

    private final RedissonClient redissonClient;

    public PriorityDispatchQueue(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public static int weightOf(String criticality) {
        if (criticality == null) return 50;
        return switch (criticality.toUpperCase()) {
            case "CRITICAL" -> 100;
            case "HIGH"     -> 75;
            case "MEDIUM"   -> 50;
            case "LOW"      -> 25;
            default         -> 50;
        };
    }

    public static double score(String criticality, OffsetDateTime createdAt) {
        long ageSeconds = createdAt == null ? 1
                : Math.max(1, ChronoUnit.SECONDS.between(createdAt, OffsetDateTime.now()));
        return (double) weightOf(criticality) * ageSeconds;
    }

    private RScoredSortedSet<String> queue() {
        return redissonClient.getScoredSortedSet(RedisCacheKeys.PRIORITY_QUEUE);
    }

    /** Add or update an incident's score in the queue. */
    public void enqueue(Incident incident) {
        queue().add(score(incident.getCriticality(), incident.getCreatedAt()),
                    incident.getId().toString());
    }

    /** ZREM — called when an incident transitions to RESOLVED. */
    public void remove(UUID incidentId) {
        queue().remove(incidentId.toString());
    }

    /** Top-N incident ids by current priority score (highest first). */
    public List<UUID> topIds(int n) {
        if (n <= 0) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (String member : queue().valueRangeReversed(0, n - 1)) {
            ids.add(UUID.fromString(member));
        }
        return ids;
    }
}
