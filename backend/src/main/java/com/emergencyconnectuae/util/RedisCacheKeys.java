// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.util;

public final class RedisCacheKeys {
    public static final String SESSION_PREFIX = "session:";
    public static final String LOCK_UNIT_PREFIX = "lock:unit:";
    public static final String LOCK_RESOURCE_PREFIX = "lock:resource:";
    public static final String LOCK_BED_PREFIX = "lock:bed:";
    public static final String CACHE_INCIDENTS_ACTIVE = "incidents:active";
    public static final String CACHE_DASHBOARD_SUMMARY = "dashboard:summary";
    public static final String PRIORITY_QUEUE = "incident:priority:queue";
    public static final String BLACKLIST_IPS = "blacklist:ips";
    public static final String BLACKLIST_COUNT_PREFIX = "blacklist:count:";
    public static final String RATE_LIMIT_PREFIX = "rl:";
    public static final String RATE_LIMIT_LOGIN_PREFIX = "rl:login:";

    private RedisCacheKeys() {}
}
