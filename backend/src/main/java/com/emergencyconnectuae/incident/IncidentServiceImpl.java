// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.incident;

import com.emergencyconnectuae.auth.User;
import com.emergencyconnectuae.auth.UserRepository;
import com.emergencyconnectuae.common.PagedResponse;
import com.emergencyconnectuae.exception.InvalidStatusTransitionException;
import com.emergencyconnectuae.exception.ResourceNotFoundException;
import com.emergencyconnectuae.incident.dto.IncidentRequest;
import com.emergencyconnectuae.incident.dto.IncidentResponse;
import com.emergencyconnectuae.triage.TriageService;
import com.emergencyconnectuae.triage.dto.TriageResponse;
import com.emergencyconnectuae.util.AuditLogger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IncidentServiceImpl implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceImpl.class);

    // OPEN -> IN_PROGRESS -> RESOLVED; only adjacent forward transitions allowed.
    private static final Map<String, Integer> STATE_ORDER =
            Map.of("OPEN", 0, "IN_PROGRESS", 1, "RESOLVED", 2);

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final TriageService triageService;
    private final PriorityDispatchQueue priorityQueue;
    private final AuditLogger auditLogger;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public IncidentServiceImpl(IncidentRepository incidentRepository, UserRepository userRepository,
                               TriageService triageService, PriorityDispatchQueue priorityQueue,
                               AuditLogger auditLogger) {
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.triageService = triageService;
        this.priorityQueue = priorityQueue;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "incidents:active", allEntries = true),
            @CacheEvict(value = "dashboard:summary", allEntries = true)
    })
    public IncidentResponse createIncident(UUID reporterId, IncidentRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reporterId));

        // AI triage (SRS 6.1) drives criticality; the dispatcher may override it.
        TriageResponse triage = triageService.analyze(request.getDescription());
        boolean overridden = request.getCriticality() != null && !request.getCriticality().isBlank();
        String criticality = overridden ? request.getCriticality().toUpperCase() : triage.getCriticality();

        Incident incident = new Incident();
        incident.setDescription(request.getDescription());
        incident.setStatus("OPEN");
        incident.setCriticality(criticality);
        incident.setLocation(point(request.getLongitude(), request.getLatitude()));
        incident.setReportedBy(reporter);
        OffsetDateTime now = OffsetDateTime.now();
        incident.setCreatedAt(now);
        incident.setUpdatedAt(now);
        incident.setIsDeleted(false);

        Incident saved = incidentRepository.save(incident);

        priorityQueue.enqueue(saved);

        auditLogger.log("INCIDENT_CREATED", "INCIDENT", saved.getId(), "SUCCESS");
        auditLogger.log(overridden ? "TRIAGE_OVERRIDDEN" : "TRIAGE_ACCEPTED",
                "INCIDENT", saved.getId(), "SUCCESS");

        return mapToResponse(saved);
    }

    @Override
    @Cacheable(value = "incident", key = "#incidentId")
    public IncidentResponse getIncident(UUID incidentId) {
        log.info("CACHE_MISS incident:{} — querying Supabase", incidentId);
        Incident incident = incidentRepository.findById(incidentId)
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Incident", "id", incidentId));
        return mapToResponse(incident);
    }

    @Override
    @Cacheable(value = "incidents:active")
    public PagedResponse<IncidentResponse> listIncidents(String status, Pageable pageable) {
        log.info("CACHE_MISS incidents:active status={} page={} — querying Supabase",
                status, pageable.getPageNumber());

        // Order by live priority-queue score: criticality weight x age (SRS 6.3).
        List<IncidentResponse> ordered = incidentRepository.findByIsDeletedFalse().stream()
                .filter(i -> status == null || status.equalsIgnoreCase(i.getStatus()))
                .sorted((a, b) -> Double.compare(
                        PriorityDispatchQueue.score(b.getCriticality(), b.getCreatedAt()),
                        PriorityDispatchQueue.score(a.getCriticality(), a.getCreatedAt())))
                .map(this::mapToResponse)
                .toList();

        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), ordered.size());
        int to = Math.min(from + pageable.getPageSize(), ordered.size());
        List<IncidentResponse> pageData = ordered.subList(from, to);
        int totalPages = (int) Math.ceil((double) ordered.size() / pageable.getPageSize());

        return new PagedResponse<>(pageData, pageable.getPageNumber(), pageable.getPageSize(),
                ordered.size(), totalPages, to >= ordered.size());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "incidents:active", allEntries = true),
            @CacheEvict(value = "incident", key = "#incidentId"),
            @CacheEvict(value = "dashboard:summary", allEntries = true)
    })
    public IncidentResponse updateStatus(UUID incidentId, String newStatus) {
        Incident incident = incidentRepository.findById(incidentId)
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Incident", "id", incidentId));

        validateTransition(incident.getStatus(), newStatus);

        incident.setStatus(newStatus);
        incident.setUpdatedAt(OffsetDateTime.now());
        Incident saved = incidentRepository.save(incident);

        if ("RESOLVED".equals(newStatus)) {
            priorityQueue.remove(saved.getId());
        } else {
            priorityQueue.enqueue(saved);
        }

        auditLogger.log("STATUS_UPDATED", "INCIDENT", saved.getId(), "SUCCESS");
        return mapToResponse(saved);
    }

    private void validateTransition(String current, String next) {
        Integer ci = STATE_ORDER.get(current);
        Integer ni = STATE_ORDER.get(next);
        if (ci == null || ni == null || ni != ci + 1) {
            throw new InvalidStatusTransitionException(
                    "Illegal transition " + current + " -> " + next
                            + " (allowed: OPEN -> IN_PROGRESS -> RESOLVED)");
        }
    }

    private Point point(Double lng, Double lat) {
        if (lng == null || lat == null) return null;
        return geometryFactory.createPoint(new Coordinate(lng, lat));
    }

    private IncidentResponse mapToResponse(Incident i) {
        return new IncidentResponse(
                i.getId(),
                i.getDescription(),
                i.getStatus(),
                i.getCriticality(),
                i.getLocation() != null ? i.getLocation().getY() : null,
                i.getLocation() != null ? i.getLocation().getX() : null,
                i.getReportedBy() != null ? i.getReportedBy().getId() : null,
                PriorityDispatchQueue.score(i.getCriticality(), i.getCreatedAt()),
                i.getCreatedAt(),
                i.getUpdatedAt()
        );
    }
}
