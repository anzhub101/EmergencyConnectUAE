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
