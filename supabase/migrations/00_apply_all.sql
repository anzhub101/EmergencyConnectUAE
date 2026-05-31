-- =====================================================================
-- EmergencyConnectUAE - consolidated migration (all 4 files in order)
-- Section: 103 | Group: 4 | Students: 1095305, 1092093, 1089507
-- Paste into Supabase SQL Editor and Run. Safe to re-run.
-- =====================================================================

-- ============ 01_extensions_and_schema.sql ============
-- =====================================================================
-- EmergencyConnectUAE - Migration 01: Extensions + Schema
-- CSC408 Distributed Information Systems | Assignment 3 | Spring 2026
-- Abu Dhabi University
--
-- Section: 103   Group: 4
-- Students: 1095305, 1092093, 1089507
-- =====================================================================

-- ---------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

-- ---------------------------------------------------------------------
-- Drop existing application tables (idempotent re-run during dev only).
-- Order respects FK dependencies. Comment out for production.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_logs      CASCADE;
DROP TABLE IF EXISTS assignments     CASCADE;
DROP TABLE IF EXISTS incidents       CASCADE;
DROP TABLE IF EXISTS emergency_units CASCADE;
DROP TABLE IF EXISTS hospitals       CASCADE;
DROP TABLE IF EXISTS users           CASCADE;

-- ---------------------------------------------------------------------
-- 7.5 users
-- id matches Supabase Auth user id (sub claim).
-- password_hash and mfa_secret are owned entirely by Supabase Auth.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id         UUID        PRIMARY KEY,
    email      TEXT        UNIQUE NOT NULL,
    role       TEXT        NOT NULL CHECK (role IN (
                   'ROLE_DISPATCHER','ROLE_RESPONDER','ROLE_HOSPITAL_ADMIN',
                   'ROLE_SYSTEM_ADMIN','ROLE_READ_ONLY')),
    is_active  BOOLEAN     DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 7.3 hospitals
-- ---------------------------------------------------------------------
CREATE TABLE hospitals (
    id             UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),
    name           TEXT                  NOT NULL,
    emirate        TEXT                  NOT NULL,
    location       GEOGRAPHY(POINT,4326),
    total_beds     INT                   NOT NULL,
    available_beds INT                   NOT NULL,
    icu_available  INT                   NOT NULL,
    is_deleted     BOOLEAN               DEFAULT false,
    created_at     TIMESTAMPTZ           DEFAULT now(),
    updated_at     TIMESTAMPTZ           DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 7.2 emergency_units
-- ---------------------------------------------------------------------
CREATE TABLE emergency_units (
    id           UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),
    type         TEXT                  NOT NULL CHECK (type IN
                       ('AMBULANCE','FIRE','POLICE','HELICOPTER')),
    status       TEXT                  NOT NULL CHECK (status IN
                       ('AVAILABLE','DISPATCHED','OFFLINE')),
    home_station TEXT,
    location     GEOGRAPHY(POINT,4326),
    hospital_id  UUID                  REFERENCES hospitals(id),
    is_deleted   BOOLEAN               DEFAULT false,
    created_at   TIMESTAMPTZ           DEFAULT now(),
    updated_at   TIMESTAMPTZ           DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 7.1 incidents
-- ---------------------------------------------------------------------
CREATE TABLE incidents (
    id          UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),
    description TEXT                  NOT NULL,
    status      TEXT                  NOT NULL CHECK (status IN
                      ('OPEN','IN_PROGRESS','RESOLVED')),
    criticality TEXT                  CHECK (criticality IN
                      ('CRITICAL','HIGH','MEDIUM','LOW')),
    location    GEOGRAPHY(POINT,4326),
    reported_by UUID                  REFERENCES users(id),
    created_at  TIMESTAMPTZ           DEFAULT now(),
    updated_at  TIMESTAMPTZ           DEFAULT now(),
    is_deleted  BOOLEAN               DEFAULT false
);

-- ---------------------------------------------------------------------
-- 7.4 assignments
-- released_at NULL = currently active assignment
-- ---------------------------------------------------------------------
CREATE TABLE assignments (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID        NOT NULL REFERENCES incidents(id),
    unit_id     UUID        NOT NULL REFERENCES emergency_units(id),
    assigned_by UUID        NOT NULL REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- 7.6 audit_logs
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        REFERENCES users(id),
    action        TEXT        NOT NULL,
    resource_type TEXT        NOT NULL,
    resource_id   UUID,
    timestamp     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address    TEXT,
    result        TEXT        NOT NULL CHECK (result IN ('SUCCESS','DENIED'))
);

-- ============ 02_indexes.sql ============
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

-- ============ 03_auth_sync_trigger.sql ============
-- =====================================================================
-- EmergencyConnectUAE - Migration 03: Auth -> users sync
-- CSC408 | Assignment 3 | Spring 2026 | Abu Dhabi University
--
-- The public.users row id MUST equal the Supabase Auth user id (sub claim).
-- This trigger keeps the application profile table in sync automatically
-- whenever an account is created in Supabase Auth. The role is read from
-- app_metadata.role (set when you create the user / via the dashboard).
-- =====================================================================

CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    meta_role TEXT;
    mapped_role TEXT;
BEGIN
    meta_role := COALESCE(NEW.raw_app_meta_data ->> 'role', 'read_only');

    mapped_role := CASE lower(meta_role)
        WHEN 'dispatcher'      THEN 'ROLE_DISPATCHER'
        WHEN 'responder'       THEN 'ROLE_RESPONDER'
        WHEN 'hospital_admin'  THEN 'ROLE_HOSPITAL_ADMIN'
        WHEN 'hospital admin'  THEN 'ROLE_HOSPITAL_ADMIN'
        WHEN 'system_admin'    THEN 'ROLE_SYSTEM_ADMIN'
        WHEN 'system admin'    THEN 'ROLE_SYSTEM_ADMIN'
        WHEN 'read_only'       THEN 'ROLE_READ_ONLY'
        WHEN 'read only'       THEN 'ROLE_READ_ONLY'
        -- allow already-prefixed values too
        WHEN 'role_dispatcher'     THEN 'ROLE_DISPATCHER'
        WHEN 'role_responder'      THEN 'ROLE_RESPONDER'
        WHEN 'role_hospital_admin' THEN 'ROLE_HOSPITAL_ADMIN'
        WHEN 'role_system_admin'   THEN 'ROLE_SYSTEM_ADMIN'
        WHEN 'role_read_only'      THEN 'ROLE_READ_ONLY'
        ELSE 'ROLE_READ_ONLY'
    END;

    INSERT INTO public.users (id, email, role, is_active, created_at)
    VALUES (NEW.id, NEW.email, mapped_role, true, now())
    ON CONFLICT (id) DO UPDATE
        SET email = EXCLUDED.email,
            role  = EXCLUDED.role;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT OR UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- ============ 04_seed.sql ============
-- =====================================================================
-- EmergencyConnectUAE - Migration 04: Seed data
-- CSC408 | Assignment 3 | Spring 2026 | Abu Dhabi University
--
-- Hospitals, emergency units, and a few sample incidents across the UAE.
-- GEOGRAPHY points are (longitude, latitude) per the WGS84/4326 convention.
-- Demo Auth users are created separately (see README / create_demo_users).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Hospitals (fixed UUIDs so units can reference them deterministically)
-- ---------------------------------------------------------------------
INSERT INTO hospitals (id, name, emirate, location, total_beds, available_beds, icu_available) VALUES
 ('11111111-1111-1111-1111-111111111101', 'Sheikh Khalifa Medical City', 'Abu Dhabi',
     ST_GeogFromText('SRID=4326;POINT(54.3667 24.4869)'), 750, 120, 18),
 ('11111111-1111-1111-1111-111111111102', 'Cleveland Clinic Abu Dhabi', 'Abu Dhabi',
     ST_GeogFromText('SRID=4326;POINT(54.3941 24.4640)'), 364, 60, 22),
 ('11111111-1111-1111-1111-111111111103', 'Rashid Hospital Trauma Centre', 'Dubai',
     ST_GeogFromText('SRID=4326;POINT(55.3270 25.2305)'), 587, 95, 30),
 ('11111111-1111-1111-1111-111111111104', 'Dubai Hospital', 'Dubai',
     ST_GeogFromText('SRID=4326;POINT(55.3130 25.2820)'), 624, 110, 25),
 ('11111111-1111-1111-1111-111111111105', 'University Hospital Sharjah', 'Sharjah',
     ST_GeogFromText('SRID=4326;POINT(55.4870 25.2920)'), 300, 48, 12);

-- ---------------------------------------------------------------------
-- Emergency units across Abu Dhabi / Dubai / Sharjah
-- ---------------------------------------------------------------------
INSERT INTO emergency_units (id, type, status, home_station, location, hospital_id) VALUES
 ('22222222-2222-2222-2222-222222222201', 'AMBULANCE',  'AVAILABLE',  'AD Corniche Station',
     ST_GeogFromText('SRID=4326;POINT(54.3500 24.4750)'), '11111111-1111-1111-1111-111111111101'),
 ('22222222-2222-2222-2222-222222222202', 'AMBULANCE',  'AVAILABLE',  'AD Khalifa City',
     ST_GeogFromText('SRID=4326;POINT(54.5800 24.4200)'), '11111111-1111-1111-1111-111111111102'),
 ('22222222-2222-2222-2222-222222222203', 'POLICE',     'AVAILABLE',  'AD Central Police',
     ST_GeogFromText('SRID=4326;POINT(54.3700 24.4800)'), NULL),
 ('22222222-2222-2222-2222-222222222204', 'FIRE',       'AVAILABLE',  'AD Civil Defence HQ',
     ST_GeogFromText('SRID=4326;POINT(54.3800 24.4600)'), NULL),
 ('22222222-2222-2222-2222-222222222205', 'HELICOPTER', 'AVAILABLE',  'AD Air Wing',
     ST_GeogFromText('SRID=4326;POINT(54.4500 24.4300)'), '11111111-1111-1111-1111-111111111101'),
 ('22222222-2222-2222-2222-222222222206', 'AMBULANCE',  'AVAILABLE',  'Dubai SZR Station',
     ST_GeogFromText('SRID=4326;POINT(55.2720 25.2050)'), '11111111-1111-1111-1111-111111111103'),
 ('22222222-2222-2222-2222-222222222207', 'POLICE',     'AVAILABLE',  'Dubai Bur Police',
     ST_GeogFromText('SRID=4326;POINT(55.3000 25.2500)'), NULL),
 ('22222222-2222-2222-2222-222222222208', 'FIRE',       'DISPATCHED', 'Dubai Civil Defence',
     ST_GeogFromText('SRID=4326;POINT(55.3200 25.2600)'), NULL),
 ('22222222-2222-2222-2222-222222222209', 'AMBULANCE',  'AVAILABLE',  'Sharjah Central',
     ST_GeogFromText('SRID=4326;POINT(55.4209 25.3463)'), '11111111-1111-1111-1111-111111111105'),
 ('22222222-2222-2222-2222-222222222210', 'POLICE',     'OFFLINE',    'Sharjah Industrial',
     ST_GeogFromText('SRID=4326;POINT(55.4500 25.3100)'), NULL);

-- ---------------------------------------------------------------------
-- Sample incidents (reported_by NULL until linked to a dispatcher account)
-- ---------------------------------------------------------------------
INSERT INTO incidents (id, description, status, criticality, location, reported_by) VALUES
 ('33333333-3333-3333-3333-333333333301',
     'Car accident on Sheikh Zayed Road, 3 vehicles involved', 'OPEN', 'HIGH',
     ST_GeogFromText('SRID=4326;POINT(55.2744 25.2110)'), NULL),
 ('33333333-3333-3333-3333-333333333302',
     'Reported cardiac arrest, elderly male unconscious in Al Wahda Mall', 'OPEN', 'CRITICAL',
     ST_GeogFromText('SRID=4326;POINT(54.3760 24.4710)'), NULL),
 ('33333333-3333-3333-3333-333333333303',
     'Kitchen fire spreading in residential tower, Marina', 'IN_PROGRESS', 'HIGH',
     ST_GeogFromText('SRID=4326;POINT(55.1390 25.0800)'), NULL),
 ('33333333-3333-3333-3333-333333333304',
     'Missing child reported near Sharjah Corniche', 'OPEN', 'LOW',
     ST_GeogFromText('SRID=4326;POINT(55.3900 25.3500)'), NULL);

