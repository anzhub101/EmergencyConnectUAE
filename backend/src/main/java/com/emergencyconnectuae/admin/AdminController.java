// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.admin;

import com.emergencyconnectuae.security.IpBlacklist;
import com.emergencyconnectuae.util.AuditLogger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * System Admin operations (SRS 4): IP blacklist management and Redis cache purge.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final IpBlacklist ipBlacklist;
    private final CacheManager cacheManager;
    private final AuditLogger auditLogger;

    public AdminController(IpBlacklist ipBlacklist, CacheManager cacheManager, AuditLogger auditLogger) {
        this.ipBlacklist = ipBlacklist;
        this.cacheManager = cacheManager;
        this.auditLogger = auditLogger;
    }

    @PostMapping("/blacklist")
    @Operation(summary = "Add an IP to the blacklist")
    public ResponseEntity<Map<String, Object>> addBlacklist(@RequestParam String ip) {
        ipBlacklist.add(ip);
        auditLogger.log("IP_BLACKLISTED", "SESSION", null, "SUCCESS");
        return ResponseEntity.ok(Map.of("ip", ip, "blacklisted", true));
    }

    @DeleteMapping("/blacklist")
    @Operation(summary = "Remove an IP from the blacklist")
    public ResponseEntity<Map<String, Object>> removeBlacklist(@RequestParam String ip) {
        boolean removed = ipBlacklist.remove(ip);
        auditLogger.log("IP_UNBLACKLISTED", "SESSION", null, "SUCCESS");
        return ResponseEntity.ok(Map.of("ip", ip, "removed", removed));
    }

    @PostMapping("/cache/purge")
    @Operation(summary = "Purge all Redis-backed Spring caches")
    public ResponseEntity<Map<String, Object>> purgeCache() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        auditLogger.log("CACHE_PURGED", "SESSION", null, "SUCCESS");
        return ResponseEntity.ok(Map.of("purged", cacheManager.getCacheNames()));
    }
}
