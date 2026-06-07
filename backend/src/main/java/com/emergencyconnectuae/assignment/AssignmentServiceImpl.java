// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.assignment;

import com.emergencyconnectuae.assignment.dto.AssignmentRequest;
import com.emergencyconnectuae.assignment.dto.AssignmentResponse;
import com.emergencyconnectuae.auth.User;
import com.emergencyconnectuae.auth.UserRepository;
import com.emergencyconnectuae.exception.ResourceLockedException;
import com.emergencyconnectuae.exception.ResourceNotFoundException;
import com.emergencyconnectuae.incident.Incident;
import com.emergencyconnectuae.incident.IncidentRepository;
import com.emergencyconnectuae.resource.EmergencyUnit;
import com.emergencyconnectuae.resource.EmergencyUnitRepository;
import com.emergencyconnectuae.util.AuditLogger;
import com.emergencyconnectuae.util.RedisCacheKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Unit assignment is the primary critical section (SRS 5.3 / 8.1).
 * A Redisson RLock (lock:unit:{unitId}) guarantees exactly-one-success on
 * concurrent assignment of the same unit.
 */
@Service
public class AssignmentServiceImpl implements AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentServiceImpl.class);

    private final AssignmentRepository assignmentRepository;
    private final IncidentRepository incidentRepository;
    private final EmergencyUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final RedissonClient redissonClient;
    private final AuditLogger auditLogger;
    // Self-reference (the proxied bean) so the @Transactional body is invoked
    // through Spring's proxy. ObjectProvider defers resolution and avoids a
    // self-referential construction cycle.
    private final ObjectProvider<AssignmentServiceImpl> self;

    public AssignmentServiceImpl(AssignmentRepository assignmentRepository, IncidentRepository incidentRepository,
                                 EmergencyUnitRepository unitRepository, UserRepository userRepository,
                                 RedissonClient redissonClient, AuditLogger auditLogger,
                                 ObjectProvider<AssignmentServiceImpl> self) {
        this.assignmentRepository = assignmentRepository;
        this.incidentRepository = incidentRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.redissonClient = redissonClient;
        this.auditLogger = auditLogger;
        this.self = self;
    }

    @Override
    public AssignmentResponse assignUnit(UUID dispatcherId, AssignmentRequest request) {
        // The Redisson lock must span the ENTIRE transaction, including its
        // commit. We therefore acquire it here, OUTSIDE the @Transactional
        // boundary, and run the DB work via a self-proxy so the commit completes
        // before the lock is released in finally. Releasing the lock inside the
        // transactional method (before commit) let a waiting thread read stale,
        // uncommitted state and double-assign the same unit.
        String lockKey = RedisCacheKeys.LOCK_UNIT_PREFIX + request.getUnitId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            // waitTime=5s (how long to retry), leaseTime=10s (auto-release) — SRS 8.1
            acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                auditLogger.log("UNIT_ASSIGN", "ASSIGNMENT", request.getUnitId(), "DENIED");
                throw new ResourceLockedException("Unit is currently being assigned");
            }
            log.info("Lock acquired {}", lockKey);
            return self.getObject().assignUnitTransactional(dispatcherId, request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceLockedException("Interrupted while acquiring unit lock");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Lock released {}", lockKey);
            }
        }
    }

    /**
     * Transactional body of {@link #assignUnit}. MUST be invoked through the
     * self-proxy (never directly) so the surrounding transaction commits before
     * the caller releases the unit lock. Not part of the public service contract.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "resourcesAvail", allEntries = true),
            @CacheEvict(value = "dashboard:summary", allEntries = true)
    })
    public AssignmentResponse assignUnitTransactional(UUID dispatcherId, AssignmentRequest request) {
        EmergencyUnit unit = unitRepository.findById(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", request.getUnitId()));
        if (!"AVAILABLE".equals(unit.getStatus())) {
            throw new ResourceLockedException("Unit not available");
        }

        Incident incident = incidentRepository.findById(request.getIncidentId())
                .orElseThrow(() -> new ResourceNotFoundException("Incident", "id", request.getIncidentId()));
        User dispatcher = userRepository.findById(dispatcherId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dispatcherId));

        unit.setStatus("DISPATCHED");
        unitRepository.save(unit);

        Assignment assignment = new Assignment();
        assignment.setIncident(incident);
        assignment.setUnit(unit);
        assignment.setAssignedBy(dispatcher);
        assignment.setAssignedAt(OffsetDateTime.now());
        Assignment saved = assignmentRepository.save(assignment);

        auditLogger.log("UNIT_ASSIGNED", "ASSIGNMENT", saved.getId(), "SUCCESS");
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "resourcesAvail", allEntries = true),
            @CacheEvict(value = "dashboard:summary", allEntries = true)
    })
    public AssignmentResponse releaseAssignment(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));

        if (assignment.getReleasedAt() == null) {
            assignment.setReleasedAt(OffsetDateTime.now());
            EmergencyUnit unit = assignment.getUnit();
            unit.setStatus("AVAILABLE");
            unitRepository.save(unit);
            assignmentRepository.save(assignment);
            auditLogger.log("ASSIGNMENT_RELEASED", "ASSIGNMENT", assignment.getId(), "SUCCESS");
        }
        return toResponse(assignment);
    }

    private AssignmentResponse toResponse(Assignment a) {
        return new AssignmentResponse(a.getId(), a.getIncident().getId(), a.getUnit().getId(),
                a.getUnit().getStatus(), a.getAssignedAt(), a.getReleasedAt());
    }
}
