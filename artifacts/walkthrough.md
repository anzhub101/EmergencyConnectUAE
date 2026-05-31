# EmergencyConnectUAE Walkthrough

Source of truth: `artifacts/EmergencyConnectUAE_SRS_v4_final.md`.

This walkthrough is the operational script for proving that the implementation satisfies the SRS. It covers environment setup, role preparation, backend/frontend execution, required demo flows, and evidence to capture for the final report.

## 1. Prerequisites

- Java 21 installed and selected for the final build.
- Redis running locally, usually `localhost:6379`.
- Supabase project ready with PostgreSQL, Auth, and PostGIS.
- Node.js available for the TypeScript frontend.
- Environment variables configured:
  - `SUPABASE_JWKS_URI`
  - `DB_URL`
  - `DB_PASSWORD`
  - `REDIS_ADDRESS`
  - `REDIS_PASSWORD`
  - `CORS_ALLOWED_ORIGINS=http://localhost:3000`

## 2. Database and Auth Setup

1. Apply migrations from `supabase/migrations/` in order:
   - `01_extensions_and_schema.sql`
   - `02_indexes.sql`
   - `03_auth_sync_trigger.sql`
   - `04_seed.sql`
2. Confirm PostGIS is enabled.
3. Confirm the six required tables exist:
   - `users`
   - `incidents`
   - `emergency_units`
   - `hospitals`
   - `assignments`
   - `audit_logs`
4. Confirm all spatial columns use `GEOGRAPHY(POINT,4326)` and have GIST indexes.
5. Create Supabase Auth users:
   - Dispatcher with `app_metadata.role=DISPATCHER` and TOTP MFA enabled
   - Responder with `app_metadata.role=RESPONDER`
   - Hospital Admin with `app_metadata.role=HOSPITAL_ADMIN`
   - System Admin with `app_metadata.role=SYSTEM_ADMIN` and TOTP MFA enabled
   - Read-Only with `app_metadata.role=READ_ONLY`
6. Log in as Dispatcher and System Admin once to verify the JWT `amr` claim includes TOTP after MFA.

Evidence to capture:
- migration success output or Supabase table screenshots
- user role metadata screenshots
- TOTP/MFA setup screenshot

## 3. Running the System

Start Redis:

```bash
redis-server
```

Start the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm run dev
```

Open:
- Frontend: `http://localhost:3000`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Use two browsers or profiles for the demo:
- Browser 1: Dispatcher
- Browser 2: Responder, Hospital Admin, System Admin, or Read-Only

## 4. Architecture Proof

During the walkthrough, show:
- Supabase Auth owns login, password hashing, JWT issuance, and TOTP MFA.
- Spring Boot validates JWT through JWKS and remains stateless.
- Redis stores revocable session metadata and handles locks, cache, rate limits, blacklist, and priority queue.
- Supabase PostgreSQL stores application data with PostGIS spatial queries.
- Frontend uses real Supabase Auth plus Spring Boot REST APIs.

Evidence to capture:
- architecture diagram
- auth flow diagram
- Redis key inventory
- DB ERD with PostGIS columns marked

## 5. Auth, Session, MFA, and Logout Flow

1. Log in as Dispatcher through Supabase Auth.
2. Complete TOTP MFA.
3. Confirm frontend calls `POST /api/v1/auth/session`.
4. Confirm Redis contains session metadata for the JWT `jti`.
5. Call a protected endpoint successfully.
6. Log out with `DELETE /api/v1/auth/session`.
7. Reuse the same JWT and show the backend returns `401`.

Expected evidence:
- successful login/MFA screenshot
- Redis session key screenshot/log
- successful API response before logout
- `401` response after logout
- audit log entries for session and denied access where applicable

## 6. Role and RBAC Walkthrough

Dispatcher:
- create incident
- run AI triage
- assign/release unit
- view audit logs
- view priority queue
- manage IP blacklist if implemented in admin controls

Responder:
- view own assignments
- update assigned incident status to `IN_PROGRESS` or `RESOLVED`
- mark self available/unavailable
- verify restricted access to audit logs and other responders' data returns `403`

Hospital Admin:
- update own hospital bed and ICU availability
- accept/reject patient assignment for own hospital
- verify other hospital data is restricted

System Admin:
- view audit logs
- purge Redis cache
- manage IP blacklist/whitelist
- view health checks
- verify incident status transition is restricted

Read-Only:
- view incidents, dashboard, and resources
- attempt mutation endpoint and show `403`

Evidence to capture:
- one success and one denied action for each role
- `audit_logs` entries with `DENIED` for forbidden attempts

## 7. Incident and Assignment Flow

1. Dispatcher creates an incident with location and description.
2. Backend sets `OPEN` and runs triage.
3. Dashboard shows the new incident.
4. Incident detail shows allowed status buttons.
5. Dispatcher starts incident: `OPEN -> IN_PROGRESS`.
6. Dispatcher resolves incident: `IN_PROGRESS -> RESOLVED`.
7. Attempt invalid backward transition and show `422`.
8. Open incident history and show audit trail.

Evidence to capture:
- create incident response
- status transition responses
- invalid transition `422`
- incident history
- audit rows

## 8. AI Triage Walkthrough

Call `POST /api/v1/triage/analyze` with descriptions that exercise the SRS rules:
- cardiac/chest pain -> `CRITICAL`
- stroke/facial droop -> `CRITICAL`
- fire/smoke -> `HIGH`
- accident/collision -> `HIGH`
- drowning/flood -> `HIGH`
- fall/fracture -> `MEDIUM`
- missing person -> `LOW`
- no keyword -> default `MEDIUM`

Then:
1. Accept one triage recommendation.
2. Override one triage recommendation.
3. Show audit actions `TRIAGE_ACCEPTED` and `TRIAGE_OVERRIDDEN`.

## 9. PostGIS Proximity Walkthrough

1. Open an incident detail page with GPS coordinates.
2. Call `GET /api/v1/resources/proximity?lat={lat}&lng={lng}&radius={m}&type={TYPE}`.
3. Show units/hospitals sorted by distance.
4. Show `distanceMetres` and `estimatedArrivalMinutes`.
5. Change type filter and show filtered results.
6. Confirm proximity results are not cached.

Evidence to capture:
- proximity API response
- SQL/report note showing parameterized `ST_DWithin` and `ST_Distance`
- frontend unit list

## 10. Redisson Lock Walkthrough

Phase 1, locks disabled:
1. Run 50 concurrent `POST /api/v1/assignments` requests against the same `unitId`.
2. Capture multiple `201 Created` responses or corrupted state.

Phase 2, locks enabled:
1. Run the same 50 concurrent requests.
2. Confirm exactly one `201 Created`.
3. Confirm the other forty-nine return `409 Conflict` with `{"error":"RESOURCE_LOCKED"}`.
4. Confirm one active assignment and one unit status update to `DISPATCHED`.
5. Capture "Lock acquired" and "Lock released" logs.

Evidence to capture:
- JMeter/k6 screenshots from both phases
- API response summary
- application logs
- database state after test

## 11. Redis Cache Walkthrough

1. Call `GET /api/v1/incidents` once.
2. Show cache miss and database query.
3. Call it nine more times.
4. Show cache hits.
5. Update incident status with `PUT /api/v1/incidents/{id}/status`.
6. Call `GET /api/v1/incidents` again.
7. Show cache eviction and fresh database query.

Repeat/verify as needed for:
- `incident:{id}`
- `resources:availability:{emirate}`
- `resource:{id}:avail`
- `dashboard:summary`

Evidence to capture:
- Redis MONITOR output or app logs containing `CACHE_HIT` / `CACHE_MISS`
- before/after status update responses

## 12. Priority Queue Walkthrough

1. Create incidents with different criticality values.
2. Show Redis priority queue key `incident:priority:queue`.
3. Show Dashboard & Incident List ordered by queue score.
4. Wait or create new incidents to show older high-criticality incidents rise above lower-priority ones.
5. Resolve an incident.
6. Confirm it is removed from the queue.

Evidence to capture:
- dashboard ordering screenshot
- Redis sorted set output/log
- resolved incident removal

## 13. Security Test Walkthrough

Run and capture all eight SRS security scenarios:

| Scenario | Expected result |
| --- | --- |
| Expired JWT | `401` |
| Valid JWT with wrong role | `403` |
| Dispatcher JWT without TOTP MFA `amr` | `403` |
| Deleted Redis session with still-valid JWT | `401` |
| SQL injection payload in description | `422`, no DB exception leak |
| `../`, `%2E%2E`, or null byte in path/query | `400` |
| 51st request within one minute | `429` with `Retry-After` |
| CORS preflight from disallowed origin | no `Access-Control-Allow-Origin` |

Also capture:
- security headers
- localhost-only `/health` behavior
- IP auto-blacklist after repeated `401`/`403`

## 14. Frontend Walkthrough

Auth View:
- Supabase login
- TOTP MFA
- backend session creation

Dashboard & Incident List:
- summary metrics
- paginated incidents
- status filters
- priority ordering
- create incident modal with AI triage prefill

Incident Detail & Action:
- incident details
- status buttons
- proximity unit list
- assign unit
- visible `409` conflict message

Admin / Logs:
- audit log table for Dispatcher/System Admin
- Hospital Admin bed/ICU editor
- System Admin cache/IP/user controls where implemented
- role-based tab visibility

Read-Only:
- read-only views work
- mutation actions disabled
- direct mutation attempt returns `403`

## 15. Swagger and Report Walkthrough

Open Swagger UI and confirm every endpoint has:
- operation summary
- request and response schema
- DTO field examples
- success and error responses
- Bearer JWT security requirement

Export OpenAPI JSON and include it in the report evidence.

Report checklist:
- system architecture and ERD
- Redis key inventory and analytical Redis section
- auth flow diagram
- Swagger/OpenAPI documentation
- concurrency evidence
- security evidence
- cache validation evidence
- self-evaluation

## 16. Final Demo Script

1. Start Redis, backend, and frontend.
2. Log in as Dispatcher and complete MFA.
3. Show Redis session creation.
4. Open dashboard.
5. Create incident and run AI triage.
6. Show priority queue ordering.
7. Open incident detail and run proximity search.
8. Assign a unit.
9. Run or show concurrent lock conflict producing `409`.
10. Change incident status and show cache eviction.
11. Log in as Responder and update own assignment/status.
12. Log in as Hospital Admin and update bed/ICU availability.
13. Log in as System Admin and show audit logs, health, cache/IP controls.
14. Log in as Read-Only and show mutation denial.
15. Open audit logs and show success/denied entries.
16. Show Swagger/OpenAPI.
17. Show captured concurrency, security, and cache evidence.

