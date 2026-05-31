// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.dashboard;

import com.emergencyconnectuae.exception.ResourceNotFoundException;
import com.emergencyconnectuae.incident.Incident;
import com.emergencyconnectuae.incident.IncidentRepository;
import com.emergencyconnectuae.incident.IncidentService;
import com.emergencyconnectuae.incident.PriorityDispatchQueue;
import com.emergencyconnectuae.incident.dto.IncidentResponse;
import com.emergencyconnectuae.resource.EmergencyUnit;
import com.emergencyconnectuae.resource.EmergencyUnitRepository;
import com.emergencyconnectuae.resource.Hospital;
import com.emergencyconnectuae.resource.HospitalRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final IncidentRepository incidentRepository;
    private final EmergencyUnitRepository unitRepository;
    private final HospitalRepository hospitalRepository;
    private final IncidentService incidentService;
    private final PriorityDispatchQueue priorityQueue;

    public DashboardServiceImpl(IncidentRepository incidentRepository, EmergencyUnitRepository unitRepository,
                                HospitalRepository hospitalRepository, IncidentService incidentService,
                                PriorityDispatchQueue priorityQueue) {
        this.incidentRepository = incidentRepository;
        this.unitRepository = unitRepository;
        this.hospitalRepository = hospitalRepository;
        this.incidentService = incidentService;
        this.priorityQueue = priorityQueue;
    }

    @Override
    @Cacheable(value = "dashboard:summary")
    public Map<String, Object> getSummary() {
        List<Incident> incidents = incidentRepository.findByIsDeletedFalse();

        Map<String, Long> byStatus = incidents.stream()
                .collect(Collectors.groupingBy(Incident::getStatus, Collectors.counting()));
        long activeIncidents = byStatus.getOrDefault("OPEN", 0L) + byStatus.getOrDefault("IN_PROGRESS", 0L);

        List<EmergencyUnit> units = unitRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted())).toList();
        long availableUnits = units.stream().filter(u -> "AVAILABLE".equals(u.getStatus())).count();

        // Bed/ICU availability grouped by emirate.
        Map<String, Map<String, Integer>> bedsByEmirate = new TreeMap<>();
        for (Hospital h : hospitalRepository.findAll()) {
            if (Boolean.TRUE.equals(h.getIsDeleted())) continue;
            Map<String, Integer> agg = bedsByEmirate.computeIfAbsent(h.getEmirate(),
                    k -> new LinkedHashMap<>(Map.of("availableBeds", 0, "icuAvailable", 0)));
            agg.merge("availableBeds", h.getAvailableBeds() == null ? 0 : h.getAvailableBeds(), Integer::sum);
            agg.merge("icuAvailable", h.getIcuAvailable() == null ? 0 : h.getIcuAvailable(), Integer::sum);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeIncidents", activeIncidents);
        summary.put("incidentsByStatus", byStatus);
        summary.put("totalUnits", (long) units.size());
        summary.put("availableUnits", availableUnits);
        summary.put("bedsByEmirate", bedsByEmirate);
        return summary;
    }

    @Override
    public List<IncidentResponse> getPriorityQueue(int topN) {
        List<IncidentResponse> result = new ArrayList<>();
        for (UUID id : priorityQueue.topIds(topN)) {
            try {
                result.add(incidentService.getIncident(id));
            } catch (ResourceNotFoundException ignored) {
                // stale queue member (incident soft-deleted) — skip
            }
        }
        return result;
    }
}
