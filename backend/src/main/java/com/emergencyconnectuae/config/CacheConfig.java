// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache abstraction backed by Redisson (SRS 8.2).
 * TTL values (milliseconds) follow the SRS cache key inventory. Caches whose
 * names appear here are created with the given TTL/max-idle; other names fall
 * back to Redisson defaults.
 *
 * Cache name      -> SRS key                           TTL
 *  incidents:active   incidents:active                  300 s
 *  incident           incident:{id}                     180 s
 *  resourcesAvail     resources:availability:{emirate}   60 s
 *  resourceAvail      resource:{id}:avail                60 s
 *  dashboard:summary  dashboard:summary                 120 s
 *
 * (Redisson's own CacheConfig type is referenced by fully-qualified name to
 *  avoid clashing with this Spring @Configuration class of the same name.)
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, org.redisson.spring.cache.CacheConfig> config = new HashMap<>();
        config.put("incidents:active", new org.redisson.spring.cache.CacheConfig(300_000L, 300_000L));
        config.put("incident", new org.redisson.spring.cache.CacheConfig(180_000L, 180_000L));
        config.put("resourcesAvail", new org.redisson.spring.cache.CacheConfig(60_000L, 60_000L));
        config.put("resourceAvail", new org.redisson.spring.cache.CacheConfig(60_000L, 60_000L));
        config.put("dashboard:summary", new org.redisson.spring.cache.CacheConfig(120_000L, 120_000L));
        return new RedissonSpringCacheManager(redissonClient, config);
    }
}
