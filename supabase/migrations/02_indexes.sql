-- =====================================================================
-- EmergencyConnectUAE - Migration 02: Indexes (GIST spatial + lookups)
-- CSC408 | Assignment 3 | Spring 2026 | Abu Dhabi University
-- =====================================================================

-- GIST spatial indexes on all GEOGRAPHY(POINT,4326) columns (SRS 3.3).
CREATE INDEX IF NOT EXISTS idx_incidents_location
    ON incidents       USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_emergency_units_location
    ON emergency_units USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_hospitals_location
    ON hospitals       USING GIST (location);

-- Common query-path indexes.
CREATE INDEX IF NOT EXISTS idx_incidents_status
    ON incidents (status) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_units_status_type
    ON emergency_units (status, type) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_hospitals_emirate
    ON hospitals (emirate) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_assignments_incident_active
    ON assignments (incident_id) WHERE released_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_assignments_unit_active
    ON assignments (unit_id) WHERE released_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp
    ON audit_logs (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource
    ON audit_logs (resource_type, resource_id);
