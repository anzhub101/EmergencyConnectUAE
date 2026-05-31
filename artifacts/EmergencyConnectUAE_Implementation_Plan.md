# EmergencyConnectUAE Implementation Plan

Source of truth: `artifacts/EmergencyConnectUAE_SRS_v4_final.md`.

This plan mirrors the SRS section by section and defines the work required for the database, backend, frontend, Redis/Redisson features, security, testing, documentation, report evidence, and demo walkthrough. A requirement is considered complete only when it is implemented, verified, and has evidence captured for the final report or demo.

## Coverage Summary

| SRS area | Implementation coverage | Evidence required |
| --- | --- | --- |
| 1. Project purpose | Build a localhost distributed emergency coordination platform with Supabase Auth, Spring Boot, Redis/Redisson, PostGIS, and TypeScript frontend. | End-to-end demo with two roles in two browsers. |
| 2. System scope | Implement four components: frontend, backend, Supabase, Redis. | Running backend, frontend, Redis, and applied Supabase migrations. |
| 3. Architecture | Implement Supabase Auth, stateless Spring Boot API, PostGIS data layer, and Redisson integration. | Architecture report and auth flow diagram. |
| 4. Roles/RBAC | Implement Dispatcher, Responder, Hospital Admin, System Admin, Read-Only. | Role-based screenshots/API responses, denied audit logs. |
| 5. Functional requirements | Implement all auth, incident, assignment, resource, dashboard, audit, and health endpoints. | Swagger/OpenAPI, API tests, frontend demo. |
| 6. Bonus requirements | Implement AI triage, PostGIS proximity, Redis priority queue. | Demo all three bonus flows. |
| 7. Data model | Implement all six tables with UUIDs, timestamps, soft deletes, and spatial fields. | ERD plus migration screenshots/output. |
| 8. Redis requirements | Implement RLock, RMapCache, Spring Cache, RRateLimiter, RSet, RScoredSortedSet. | Redis key inventory, logs, cache/lock evidence. |
| 9. API requirements | Use `/api/v1`, JSON, REST verbs, expected status codes, pagination envelope. | Swagger/OpenAPI and API test results. |
| 10. Security | Implement JWT/JWKS, MFA, validation, IP blacklist, CORS, env config, headers. | Security test matrix with pass/fail evidence. |
| 11. Concurrency | Run two-phase lock test. | JMeter/k6 screenshots and lock logs. |
| 12. Frontend | Build four real-data TypeScript screens. | Demo video and screenshots. |
| 13. Backend package structure | Use feature-based packages with lab pattern inside each domain. | Source tree screenshot or package listing. |
| 14. Maven dependencies | Include all SRS dependencies. | `pom.xml` plus successful build. |
| 15. Swagger/OpenAPI | Document every endpoint with schemas, examples, status codes, Bearer JWT. | Swagger UI/OpenAPI JSON export. |
| 16. Testing | Run concurrency, security, and cache validation tests. | Test logs/screenshots. |
| 17. Non-functional requirements | Demonstrate scalability design, reliability, performance, maintainability. | Report section with concrete design choices. |
| 18. Deliverables | Produce project, frontend, demo video, reports, evidence, self-evaluation. | Final submission checklist. |

## 1. Baseline and Environment

- Keep the repository organized as:
  - `backend/`: Java 21 / Spring Boot 3 REST API on `localhost:8080`
  - `frontend/`: TypeScript React/Vue app on `localhost:3000`
  - `supabase/`: SQL migrations and seed data
  - `artifacts/`: SRS, plan, tasks, walkthrough, report evidence
- Run entirely on localhost, with optional LAN demo support by replacing frontend API config with the backend LAN IP.
- Configure two-browser demo:
  - Chrome: Dispatcher
  - Firefox or another browser profile: Responder, Hospital Admin, System Admin, or Read-Only
- Use environment variables only:
  - `SUPABASE_JWKS_URI`
  - `DB_URL`
  - `DB_PASSWORD`
  - `REDIS_ADDRESS`
  - `REDIS_PASSWORD`
  - `CORS_ALLOWED_ORIGINS`
- Keep `.env` and secrets out of version control.
- Add the CSC408 source header to every Java source file: section number, group number, student IDs, and names.

## 2. Supabase Database and Auth

- Apply and verify migrations:
  - `01_extensions_and_schema.sql`
  - `02_indexes.sql`
  - `03_auth_sync_trigger.sql`
  - `04_seed.sql`
- Enable PostGIS.
- Implement the six SRS tables:
  - `users`
  - `incidents`
  - `emergency_units`
  - `hospitals`
  - `assignments`
  - `audit_logs`
- Use UUID primary keys, timestamps, and soft deletes where required.
- Use `GEOGRAPHY(POINT,4326)` for incident, unit, and hospital locations.
- Add GIST indexes on all spatial columns.
- Enforce database checks:
  - incident status: `OPEN`, `IN_PROGRESS`, `RESOLVED`
  - criticality: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`
  - unit type: `AMBULANCE`, `FIRE`, `POLICE`, `HELICOPTER`
  - unit status: `AVAILABLE`, `DISPATCHED`, `OFFLINE`
  - audit result: `SUCCESS`, `DENIED`
- Configure Supabase Auth:
  - store role in `app_metadata.role`
  - create Dispatcher, Responder, Hospital Admin, System Admin, and Read-Only demo users
  - enable TOTP MFA for Dispatcher and System Admin
  - verify JWT includes `sub`, `email`, `app_metadata.role`, `jti`, expiry, and `amr` when MFA is complete
- Keep password hashes and MFA secrets only in Supabase Auth, not application tables.

## 3. Backend Package Structure

- Use feature-based packages under `com.emergencyconnectuae`.
- Each domain package must follow the lab pattern: Service interface -> ServiceImpl -> Controller -> Repository.
- Required packages:
  - `auth`: `AuthController`, `AuthService`, `AuthServiceImpl`, `User`, `UserRepository`, session DTOs
  - `incident`: incident entity/repository/service/controller, create/status/history DTOs
  - `assignment`: assignment entity/repository/service/controller, assign/release DTOs
  - `resource`: emergency unit, hospital, resource repository, hospital repository, availability/proximity DTOs
  - `dashboard`: summary service/controller and priority view response
  - `audit`: audit log entity/repository/service/controller
  - `triage`: deterministic keyword triage service/controller
  - `security`: JWT validation, rate limiting, RBAC, filter chain
  - `config`: Redisson, cache, Swagger, CORS, security headers
  - `exception`: global exception mapping
  - `common`: `PagedResponse<T>` and shared enums
  - `util`: `RedisCacheKeys`, `AuditLogger`, location helpers
- Map spatial entity fields to `org.locationtech.jts.geom.Point`.
- Annotate spatial columns with `@Column(columnDefinition = "geography(Point,4326)")`.

## 4. Backend Dependencies

- Use Java 21 and Spring Boot 3.
- Include Maven dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-security`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-oauth2-resource-server`
  - `org.hibernate.orm:hibernate-spatial`
  - `org.postgresql:postgresql`
  - `org.redisson:redisson-spring-boot-starter:3.27.2`
  - `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`
- Configure Hibernate/PostgreSQL with spatial support.
- Use Spring Boot BOM-managed versions when possible.

## 5. Authentication, Session Management, and RBAC

- Implement `POST /api/v1/auth/session`.
  - Validate JWT via Supabase JWKS.
  - Extract `jti`, `sub`, role, client IP, and expiry.
  - Write Redis session metadata with TTL matching JWT expiry.
- Implement `DELETE /api/v1/auth/session`.
  - Remove the Redis session entry.
  - Reject the same JWT on subsequent calls, even before natural expiry.
- Store Redis session metadata:
  - key semantics: `session:{jti}`
  - fields: `userId`, `role`, `ipAddress`, `expiresAt`
- Enforce MFA by checking JWT `amr` for `{"method":"totp"}`.
- Require MFA for Dispatcher and System Admin.
- Use `@PreAuthorize` or equivalent RBAC on every endpoint.
- Role capabilities:
  - Dispatcher: create incidents, assign/release units, view audit logs, manage IP blacklist entries, use AI triage, view priority queue
  - Responder: view own assignments, update assigned incident status to `IN_PROGRESS` or `RESOLVED`, mark self available/unavailable
  - Hospital Admin: update own hospital bed/ICU availability, accept/reject patient assignments for own hospital
  - System Admin: manage Supabase users/admin profile records, view audit logs, purge Redis cache, manage IP blacklist/whitelist, view health checks
  - Read-Only: view paginated incidents, dashboard summary, and resource availability only
- Write `DENIED` audit logs for RBAC failures.

## 6. Core Functional Endpoints

- Auth:
  - `POST /api/v1/auth/session`
  - `DELETE /api/v1/auth/session`
- Incidents:
  - `POST /api/v1/incidents`
  - `GET /api/v1/incidents`
  - `GET /api/v1/incidents/{id}`
  - `PUT /api/v1/incidents/{id}/status`
  - `GET /api/v1/incidents/{id}/history`
- Incident state machine:
  - valid: `OPEN -> IN_PROGRESS -> RESOLVED`
  - invalid backward transitions return `422`
- Assignments:
  - `POST /api/v1/assignments`
  - `DELETE /api/v1/assignments/{id}`
  - Responder support: endpoint/service query for own active assignments
  - Hospital Admin support: accept/reject patient assignment behavior
- Resources:
  - `GET /api/v1/resources`
  - `GET /api/v1/resources/{id}/availability`
  - `PUT /api/v1/resources/{id}/availability`
  - `PUT /api/v1/resources/{id}/reserve`
  - `GET /api/v1/resources/proximity`
  - Responder availability toggle behavior
- Dashboard and audit:
  - `GET /api/v1/dashboard/summary`
  - priority queue view for top-N incidents
  - `GET /api/v1/audit/logs`
  - `GET /api/v1/health`
- Admin/security operations:
  - manual IP blacklist add/remove
  - Redis cache purge
  - user/admin profile management aligned with Supabase roles
- Every list endpoint must be paginated with default `page=0`, default `size=20`, max `size=100`.
- Use response envelope: `{ data[], page, size, totalElements, totalPages, last }`.

## 7. Redis and Redisson Requirements

- Use a single `RedissonClient` bean.
- Do not use direct Jedis or Lettuce APIs.
- Define all keys in `RedisCacheKeys`.
- Implement mandatory RLocks:
  - `lock:unit:{unitId}` for `POST /assignments`
  - `lock:resource:{id}` for `PUT /resources/{id}/reserve`
  - `lock:bed:{hospitalId}` for hospital bed reservation
- Use `tryLock(5, 10, TimeUnit.SECONDS)`.
- On lock contention return `409 Conflict` with `{"error":"RESOURCE_LOCKED"}`.
- Log "Lock acquired" and "Lock released" for evidence.
- Implement Spring Cache backed by Redisson:
  - `incidents:active`, TTL 300 seconds
  - `incident:{id}`, TTL 180 seconds
  - `resources:availability:{emirate}`, TTL 60 seconds
  - `resource:{id}:avail`, TTL 60 seconds
  - `dashboard:summary`, TTL 120 seconds
- Evict affected cache entries after incident creation/status changes, reservation, availability update, and assignment release.
- Do not cache:
  - audit logs
  - auth/session endpoints
  - PostGIS proximity queries
  - critical live availability counts used for incoming patients
- Implement `RRateLimiter`:
  - API: 60 requests per minute per user/IP
  - login/session: 5 attempts per minute per IP
- Implement `RSet "blacklist:ips"`:
  - check before normal processing
  - auto-add after 10 or more `401`/`403` responses in 5 minutes
  - System Admin add/remove
- Implement `RScoredSortedSet` priority queue:
  - key: `incident:priority:queue`
  - score: criticality weight times age in seconds
  - weights: `CRITICAL=100`, `HIGH=75`, `MEDIUM=50`, `LOW=25`
  - remove resolved incidents from the queue

## 8. Bonus Features

- AI triage:
  - `POST /api/v1/triage/analyze`
  - first-match-wins keyword rules
  - response fields: `criticality`, `confidence`, `recommendedUnits`, `recommendedHospitalTier`, `matchedKeywords`, `dispatchCount`
  - support accept/override path and audit `TRIAGE_ACCEPTED` or `TRIAGE_OVERRIDDEN`
- PostGIS proximity:
  - parameterized query only, no string interpolation
  - accepts `lat`, `lng`, `radius`, `type`
  - uses `ST_DWithin` and `ST_Distance`
  - returns id, name, type, status, distanceMetres, estimatedArrivalMinutes
  - speeds: ambulance 60 km/h, helicopter 200 km/h, police 80 km/h, fire 50 km/h
- Redis priority queue:
  - Dashboard & Incident List must show incidents reordering by priority
  - demo must create multiple incidents of different criticality and show ordering changes

## 9. API Requirements

- Base path: `/api/v1`.
- JSON request/response only.
- Stateless: no in-process HTTP sessions.
- Resource-oriented URIs; nouns only.
- HTTP verbs:
  - `GET` for reads
  - `POST` for creates
  - `PUT` for updates
  - `DELETE` for removal/release/logout
- Required status codes:
  - `200 OK`
  - `201 Created`
  - `400 Bad Request`
  - `401 Unauthorized`
  - `403 Forbidden`
  - `404 Not Found`
  - `409 Conflict`
  - `422 Unprocessable Entity`
  - `429 Too Many Requests`
- Apply Bean Validation to all DTOs.
- Return structured field-level validation errors.

## 10. Security Requirements

- Configure `JwtDecoder` from `${SUPABASE_JWKS_URI}`.
- Do not store raw JWT signing secrets.
- Reject expired, tampered, missing, or revoked tokens with `401`.
- Reject MFA-required roles without `amr` TOTP with `403`.
- Reject path traversal patterns `../`, `%2E%2E`, and null bytes with `400`.
- Use parameterized JPA/native queries only.
- Configure CORS from `${CORS_ALLOWED_ORIGINS}`.
- Allow only `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`.
- Add headers:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Content-Security-Policy: default-src 'self'`
  - `Strict-Transport-Security: max-age=31536000`
  - `Referrer-Policy: no-referrer`
- Restrict `/health` and `/actuator/**` to localhost CIDR.

## 11. Frontend Requirements

- Build a TypeScript frontend with real Supabase Auth and real Spring Boot API integration.
- No mock-only screens.
- Auth View:
  - email/password login
  - TOTP MFA flow
  - create backend Redis session after Supabase login
- Dashboard & Incident List:
  - summary metrics
  - paginated incident table
  - status filter chips
  - priority queue ordering
  - create incident modal
  - AI triage recommendation and criticality prefill
- Incident Detail & Action:
  - incident details
  - status transition buttons
  - proximity-based unit list
  - assign button
  - visible `409` lock conflict message
- Admin / Logs:
  - audit log table for Dispatcher and System Admin
  - resource availability editor for Hospital Admin
  - role-based tab/action visibility from JWT claims
- Explicitly support Read-Only role by disabling mutation actions and showing `403` if attempted directly.

## 12. Swagger and OpenAPI

- Enable SpringDoc.
- Expose:
  - Swagger UI: `http://localhost:8080/swagger-ui.html`
  - OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Every endpoint must include:
  - `@Operation(summary = "...")`
  - schema descriptions and examples on DTO fields
  - `@ApiResponse` for success and expected failures
  - Bearer JWT `SecurityRequirement`
  - at least one usage example in the report

## 13. Testing and Evidence

- Concurrency:
  - Phase 1, locks disabled: 50 concurrent `POST /assignments` for same unit; prove race condition or corrupted state
  - Phase 2, locks enabled: exactly one `201`, forty-nine `409`, one active assignment, unit status changed once
  - capture JMeter/k6 screenshots and lock logs
- Security:
  - expired JWT -> `401`
  - wrong role -> `403`
  - Dispatcher without MFA -> `403`
  - deleted Redis session with still-valid JWT -> `401`
  - SQL injection payload -> `422`, no DB exception leak
  - path traversal in path/query -> `400`
  - 51st request within one minute -> `429` with `Retry-After`
  - invalid CORS origin -> no `Access-Control-Allow-Origin`
- Cache:
  - first `GET /incidents` is miss
  - next nine `GET /incidents` are hits
  - `PUT /incidents/{id}/status` evicts
  - next read is miss
  - capture Redis MONITOR output or app `CACHE_HIT` / `CACHE_MISS` logs
- Backend tests:
  - DTO validation
  - RBAC
  - MFA
  - state transitions
  - locks
  - caching
  - proximity validation
  - triage rules
- Frontend tests/manual checks:
  - login/MFA
  - role-specific visibility
  - pagination
  - filters
  - 409 conflict message
  - admin/hospital update flow

## 14. Non-Functional Requirements

- Scalability:
  - stateless backend
  - Redis session store external to process
  - paginated list APIs
- Reliability:
  - RLock for exactly-one-success assignment/reservation
  - soft deletes preserve referential integrity
  - eager cache eviction prevents stale reads after writes
- Performance:
  - cache high-read, low-write data
  - PostGIS GIST indexes for proximity queries
- Maintainability:
  - feature package lab pattern
  - centralized Redis key constants
  - centralized exception mapping

## 15. Reports and Deliverables

- Full Java Spring Boot project with required CSC408 headers.
- TypeScript frontend with real Supabase Auth and REST API integration.
- Recorded demo video showing:
  - login and MFA
  - Redis session creation
  - dashboard
  - incident creation with AI triage
  - priority ordering
  - PostGIS proximity search
  - unit assignment
  - concurrent `409` lock conflict
  - status update and cache eviction
  - audit log entries
- Architecture report:
  - system architecture
  - DB ERD covering all six tables
  - PostGIS columns marked
  - Redis key inventory
  - auth flow diagram
- Swagger/OpenAPI report:
  - all endpoints
  - schemas
  - auth requirements
  - examples
- Concurrency test report.
- Security test report.
- Cache validation report.
- Analytical Redis section:
  - all mandatory Redis roles
  - when not to use each
  - tradeoffs
- Self-evaluation:
  - design tradeoffs
  - known limitations
  - concrete improvements
