// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource;

import com.emergencyconnectuae.common.PagedResponse;
import com.emergencyconnectuae.resource.dto.ProximityResponse;
import com.emergencyconnectuae.resource.dto.ResourceAvailabilityRequest;
import com.emergencyconnectuae.resource.dto.ResourceResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ResourceService {
    PagedResponse<ResourceResponse> listAvailability(String emirate, Pageable pageable);
    ResourceResponse getAvailability(UUID hospitalId);
    ResourceResponse updateAvailability(UUID hospitalId, ResourceAvailabilityRequest request);
    ResourceResponse reserveBed(UUID hospitalId);
    List<ProximityResponse> proximity(double lat, double lng, double radiusMetres, String type);
}
