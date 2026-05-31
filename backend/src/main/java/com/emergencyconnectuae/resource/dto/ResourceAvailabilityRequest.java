// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /resources/{id}/availability — Hospital Admin updates bed / ICU counts.
 */
public class ResourceAvailabilityRequest {

    @NotNull
    @Min(0)
    @Schema(description = "Total bed capacity of the hospital", example = "750")
    private Integer totalBeds;

    @NotNull
    @Min(0)
    @Schema(description = "Currently available (free) beds", example = "120")
    private Integer availableBeds;

    @NotNull
    @Min(0)
    @Schema(description = "Available ICU beds", example = "18")
    private Integer icuAvailable;

    public Integer getTotalBeds() { return totalBeds; }
    public void setTotalBeds(Integer totalBeds) { this.totalBeds = totalBeds; }
    public Integer getAvailableBeds() { return availableBeds; }
    public void setAvailableBeds(Integer availableBeds) { this.availableBeds = availableBeds; }
    public Integer getIcuAvailable() { return icuAvailable; }
    public void setIcuAvailable(Integer icuAvailable) { this.icuAvailable = icuAvailable; }
}
