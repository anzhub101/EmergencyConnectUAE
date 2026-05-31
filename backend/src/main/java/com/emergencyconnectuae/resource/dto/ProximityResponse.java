// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource.dto;

import java.util.UUID;

/**
 * PostGIS proximity result (SRS 6.2).
 * estimatedArrivalMinutes = distanceMetres / avgSpeedMs, by unit type:
 *   AMBULANCE 60 km/h, HELICOPTER 200 km/h, POLICE 80 km/h, FIRE 50 km/h.
 */
public class ProximityResponse {
    private UUID id;
    private String name;
    private String type;
    private String status;
    private double distanceMetres;
    private double estimatedArrivalMinutes;

    public ProximityResponse(UUID id, String name, String type, String status,
                             double distanceMetres, double estimatedArrivalMinutes) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.distanceMetres = distanceMetres;
        this.estimatedArrivalMinutes = estimatedArrivalMinutes;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public double getDistanceMetres() { return distanceMetres; }
    public double getEstimatedArrivalMinutes() { return estimatedArrivalMinutes; }
}
