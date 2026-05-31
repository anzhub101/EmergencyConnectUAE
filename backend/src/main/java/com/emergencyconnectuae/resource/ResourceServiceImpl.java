// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource;

import com.emergencyconnectuae.common.PagedResponse;
import com.emergencyconnectuae.exception.ResourceLockedException;
import com.emergencyconnectuae.exception.ResourceNotFoundException;
import com.emergencyconnectuae.resource.dto.ProximityResponse;
import com.emergencyconnectuae.resource.dto.ResourceAvailabilityRequest;
import com.emergencyconnectuae.resource.dto.ResourceResponse;
import com.emergencyconnectuae.util.AuditLogger;
import com.emergencyconnectuae.util.RedisCacheKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Hospital availability (beds/ICU) + emergency-unit proximity search.
 * Availability views are cached (SRS 8.2); proximity is never cached because GPS
 * positions change faster than any useful TTL (SRS 6.2).
 */
@Service
public class ResourceServiceImpl implements ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceServiceImpl.class);

    // Average speeds (km/h) used for estimatedArrivalMinutes (SRS 6.2).
    private static final Map<String, Double> SPEED_KMH =
            Map.of("AMBULANCE", 60.0, "HELICOPTER", 200.0, "POLICE", 80.0, "FIRE", 50.0);

    private final HospitalRepository hospitalRepository;
    private final EmergencyUnitRepository unitRepository;
    private final RedissonClient redissonClient;
    private final AuditLogger auditLogger;

    public ResourceServiceImpl(HospitalRepository hospitalRepository, EmergencyUnitRepository unitRepository,
                               RedissonClient redissonClient, AuditLogger auditLogger) {
        this.hospitalRepository = hospitalRepository;
        this.unitRepository = unitRepository;
        this.redissonClient = redissonClient;
        this.auditLogger = auditLogger;
    }

    @Override
    @Cacheable(value = "resourcesAvail",
            key = "(#emirate == null ? 'ALL' : #emirate.toUpperCase()) + ':' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public PagedResponse<ResourceResponse> listAvailability(String emirate, Pageable pageable) {
        log.info("CACHE_MISS resources:availability:{} — querying Supabase", emirate == null ? "ALL" : emirate);
        Page<Hospital> page = (emirate == null || emirate.isBlank())
                ? hospitalRepository.findByIsDeletedFalse(pageable)
                : hospitalRepository.findByEmirateIgnoreCaseAndIsDeletedFalse(emirate, pageable);
        return new PagedResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }

    @Override
    @Cacheable(value = "resourceAvail", key = "#hospitalId")
    public ResourceResponse getAvailability(UUID hospitalId) {
        log.info("CACHE_MISS resource:{}:avail — querying Supabase", hospitalId);
        return toResponse(findHospital(hospitalId));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "resourceAvail", key = "#hospitalId"),
            @CacheEvict(value = "resourcesAvail", allEntries = true),
            @CacheEvict(value = "dashboard:summary", allEntries = true)
    })
    public ResourceResponse updateAvailability(UUID hospitalId, ResourceAvailabilityRequest request) {
        Hospital hospital = findHospital(hospitalId);
        hospital.setTotalBeds(request.getTotalBeds());
        hospital.setAvailableBeds(Math.min(request.getAvailableBeds(), request.getTotalBeds()));
        hospital.setIcuAvailable(request.getIcuAvailable());
        Hospital saved = hospitalRepository.save(hospital);
        auditLogger.log("RESOURCE_AVAILABILITY_UPDATED", "RESOURCE", saved.getId(), "SUCCESS");
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "resourceAvail", key = "#hospitalId"),
            @CacheEvict(value = "resourcesAvail", allEntries = true),
            @CacheEvict(value = "dashboard:summary", allEntries = true)
    })
    public ResourceResponse reserveBed(UUID hospitalId) {
        // lock:bed:{hospitalId} (SRS 8.1) guards the bed-count critical section.
        String lockKey = RedisCacheKeys.LOCK_BED_PREFIX + hospitalId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ResourceLockedException("Resource is currently being reserved");
            }
            log.info("Lock acquired {}", lockKey);

            Hospital hospital = findHospital(hospitalId);
            if (hospital.getAvailableBeds() == null || hospital.getAvailableBeds() <= 0) {
                throw new ResourceLockedException("No available beds to reserve");
            }
            hospital.setAvailableBeds(hospital.getAvailableBeds() - 1);
            Hospital saved = hospitalRepository.save(hospital);
            auditLogger.log("RESOURCE_RESERVED", "RESOURCE", saved.getId(), "SUCCESS");
            return toResponse(saved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceLockedException("Interrupted while acquiring resource lock");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Lock released {}", lockKey);
            }
        }
    }

    @Override
    public List<ProximityResponse> proximity(double lat, double lng, double radiusMetres, String type) {
        String unitType = (type == null || type.isBlank()) ? "ALL" : type.toUpperCase();
        return unitRepository.findNearbyAvailable(lat, lng, radiusMetres, unitType).stream()
                .map(row -> {
                    UUID id = UUID.fromString(row[0].toString());
                    String t = (String) row[1];
                    String status = (String) row[2];
                    String homeStation = (String) row[3];
                    double distance = ((Number) row[4]).doubleValue();
                    return new ProximityResponse(id, homeStation, t, status, distance, etaMinutes(t, distance));
                })
                .toList();
    }

    private double etaMinutes(String unitType, double distanceMetres) {
        double speedKmh = SPEED_KMH.getOrDefault(unitType, 60.0);
        double speedMs = speedKmh * 1000.0 / 3600.0;
        return Math.round((distanceMetres / speedMs / 60.0) * 100.0) / 100.0;
    }

    private Hospital findHospital(UUID id) {
        return hospitalRepository.findById(id)
                .filter(h -> !Boolean.TRUE.equals(h.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", "id", id));
    }

    private ResourceResponse toResponse(Hospital h) {
        return new ResourceResponse(h.getId(), h.getName(), h.getEmirate(),
                h.getTotalBeds(), h.getAvailableBeds(), h.getIcuAvailable());
    }
}
