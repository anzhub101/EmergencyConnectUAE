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
