// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.security;

import com.emergencyconnectuae.util.RedisCacheKeys;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * IP blacklist (SRS 10.4). Backed by a Redisson RSet for O(1) lookup at filter
 * entry. IPs generating 10+ 401/403 responses within any 5-minute window are
 * auto-added; System Admin may add/remove entries manually.
 */
@Component
public class IpBlacklist {

    private static final int DENIAL_THRESHOLD = 10;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final RedissonClient redissonClient;

    public IpBlacklist(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    private RSet<String> set() {
        return redissonClient.getSet(RedisCacheKeys.BLACKLIST_IPS);
    }

    public boolean isBlacklisted(String ip) {
        return ip != null && set().contains(ip);
    }

    public void add(String ip) {
        set().add(ip);
    }

    public boolean remove(String ip) {
        return set().remove(ip);
    }

    /** Count a 401/403 against an IP; auto-blacklist past the threshold. */
    public void recordDenial(String ip) {
        if (ip == null) return;
        RAtomicLong counter = redissonClient.getAtomicLong(RedisCacheKeys.BLACKLIST_COUNT_PREFIX + ip);
        long count = counter.incrementAndGet();
        if (count == 1L) {
            counter.expire(WINDOW);
        }
        if (count >= DENIAL_THRESHOLD) {
            add(ip);
        }
    }
}
