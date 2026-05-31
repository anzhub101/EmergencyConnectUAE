// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * Hospital bed/ICU availability view (SRS 5.3 / 7.3).
 */
public class ResourceResponse implements Serializable {
    private UUID id;
    private String name;
    private String emirate;
    private Integer totalBeds;
    private Integer availableBeds;
    private Integer icuAvailable;

    public ResourceResponse(UUID id, String name, String emirate,
                            Integer totalBeds, Integer availableBeds, Integer icuAvailable) {
        this.id = id;
        this.name = name;
        this.emirate = emirate;
        this.totalBeds = totalBeds;
        this.availableBeds = availableBeds;
        this.icuAvailable = icuAvailable;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmirate() { return emirate; }
    public Integer getTotalBeds() { return totalBeds; }
    public Integer getAvailableBeds() { return availableBeds; }
    public Integer getIcuAvailable() { return icuAvailable; }
}
