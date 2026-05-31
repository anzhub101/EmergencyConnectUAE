// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.audit.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AuditLogResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String action;
    private String resourceType;
    private UUID resourceId;
    private OffsetDateTime timestamp;
    private String ipAddress;
    private String result;

    public AuditLogResponse() {}

    public AuditLogResponse(UUID id, UUID userId, String userEmail, String action, String resourceType, UUID resourceId, OffsetDateTime timestamp, String ipAddress, String result) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.result = result;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
