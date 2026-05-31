// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.resource;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

/**
 * emergency_units (SRS 7.2)
 * type: AMBULANCE | FIRE | POLICE | HELICOPTER
 * status: AVAILABLE | DISPATCHED | OFFLINE
 */
@Entity
@Table(name = "emergency_units")
public class EmergencyUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    private String homeStation;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "hospital_id")
    private UUID hospitalId;

    private Boolean isDeleted = false;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHomeStation() { return homeStation; }
    public void setHomeStation(String homeStation) { this.homeStation = homeStation; }
    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }
    public UUID getHospitalId() { return hospitalId; }
    public void setHospitalId(UUID hospitalId) { this.hospitalId = hospitalId; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
}
