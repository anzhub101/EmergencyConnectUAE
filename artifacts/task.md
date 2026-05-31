# EmergencyConnectUAE Development Tasks

Source of truth: `artifacts/EmergencyConnectUAE_SRS_v4_final.md`.

Legend:
- `[x]` complete and verified
- `[/]` partially complete or implementation exists but needs verification/evidence
- `[ ]` required by SRS and still to complete

## 1. Environment and Submission Foundation

- `[x]` Confirm repository layout matches SRS scope.
  - `[x]` `backend/` exists for Spring Boot API.
  - `[x]` `frontend/` exists for TypeScript app.
  - `[x]` `supabase/` exists for migrations.
  - `[x]` `artifacts/` exists for plans, SRS, walkthrough, and evidence.
- `[x]` Install/use JDK 21 for final build and demo. (OpenJDK 21.0.11; build emits bytecode v65)
- `[x]` Configure backend runtime environment variables. (all wired in `application.yml`; `.env` values are the operator's to set at run time)
  - `[x]` `SUPABASE_JWKS_URI` (wired in `application.yml`)
  - `[x]` `DB_URL` (wired in `application.yml`)
  - `[x]` `DB_PASSWORD` (wired; actual secret set in `.env` at run time)
  - `[x]` `REDIS_ADDRESS` (wired in `application.yml`)
  - `[x]` `REDIS_PASSWORD` (wired in `application.yml`)
  - `[x]` `CORS_ALLOWED_ORIGINS` (wired in `application.yml`)
- `[x]` Confirm `.env` is ignored and no secrets are committed. (`.gitignore`: `.env`, `**/.env`, `!**/.env.example`; no `.env` present)
- `[x]` Add required CSC408 header to every Java source file. (all 69 `.java` files carry the header)

## 2. Supabase Database and Auth

- `[x]` Database migrations.
  - `[x]` Create schema migration for `users`, `incidents`, `emergency_units`, `hospitals`, `assignments`, `audit_logs`.
  - `[x]` Enable PostGIS.
  - `[x]` Add GIST indexes for spatial columns.
  - `[x]` Add auth sync trigger migration.
  - `[x]` Add seed migration.
  - `[x]` Apply migrations to the hosted/local Supabase project used for demo.
  - `[x]` Capture migration/application evidence for the report.
- `[x]` Verify data model against SRS Section 7.
  - `[x]` UUID primary keys.
  - `[x]` timestamps where required.
  - `[x]` soft delete fields where required.
  - `[x]` `GEOGRAPHY(POINT,4326)` for incident, unit, hospital locations.
  - `[x]` status/type/result constraints.
- `[x]` Configure Supabase Auth users.
  - `[x]` Dispatcher with TOTP MFA. (user created; TOTP enrollment complete)
  - `[x]` Responder.
  - `[x]` Hospital Admin.
  - `[x]` System Admin with TOTP MFA. (user created; TOTP enrollment complete)
  - `[x]` Read-Only.
  - `[x]` Verify `app_metadata.role` appears in JWT. (role set in `raw_app_meta_data`, synced to `public.users` via trigger)
  - `[x]` Verify `amr` includes TOTP after MFA.

## 3. Backend Foundation

- `[x]` Spring Boot foundation.
  - `[x]` Create `pom.xml` with SRS dependencies.
  - `[x]` Create Maven wrapper.
  - `[x]` Create `BackendApplication.java`.
  - `[x]` Create `application.yml`.
  - `[x]` Build with JDK 21 without override. (`pom.xml` java.version=21; `./mvnw clean package` -> BUILD SUCCESS, bytecode v65)
- `[x]` Feature-based package structure.
  - `[x]` `auth`
  - `[x]` `incident`
  - `[x]` `assignment`
  - `[x]` `resource`
  - `[x]` `dashboard`
  - `[x]` `audit`
  - `[x]` `triage`
  - `[x]` `security`
  - `[x]` `config`
  - `[x]` `exception`
  - `[x]` `common`
  - `[x]` `util`
- `[x]` Model and persistence.
  - `[x]` Entity/repository coverage for all six tables.
  - `[x]` PostGIS `Point` mapping.
  - `[x]` Verify generated SQL/bindings against applied Supabase schema.

## 4. Security, Auth, Sessions, and RBAC

- `[x]` JWT/JWKS.
  - `[x]` Configure resource server/JWT decoder.
  - `[x]` Reject missing/invalid/expired JWTs.
  - `[x]` Test expired/tampered JWT returns `401`.
- `[x]` Redis sessions.
  - `[x]` `POST /api/v1/auth/session`.
  - `[x]` `DELETE /api/v1/auth/session`.
  - `[x]` `RMapCache` session lookup.
  - `[x]` Verify logout revokes still-valid JWT with `401`.
- `[x]` MFA.
  - `[x]` Check `amr` claim for TOTP.
  - `[x]` Verify Dispatcher without MFA returns `403`.
  - `[x]` Verify System Admin without MFA returns `403`.
- `[x]` RBAC.
  - `[x]` Dispatcher restrictions.
  - `[x]` Read-Only mutation denial.
  - `[x]` Verify Responder only sees own assignments.
  - `[x]` Verify Responder can mark self available/unavailable.
  - `[x]` Verify Hospital Admin can update only own hospital availability.
  - `[x]` Verify Hospital Admin accept/reject patient assignment flow.
  - `[x]` Verify System Admin user/cache/IP operations.
  - `[x]` Verify all denials are written to `audit_logs` with `DENIED`.
- `[x]` Security filters.
  - `[x]` Rate limiting with `RRateLimiter`.
  - `[x]` IP blacklist with `RSet`.
  - `[x]` path traversal rejection.
  - `[x]` security headers.
  - `[x]` CORS configuration source.
  - `[x]` Verify 51st request returns `429` with `Retry-After`.
  - `[x]` Verify invalid CORS origin has no allow-origin header.

## 5. Core Backend Functional Requirements

- `[x]` Auth endpoints.
  - `[x]` `POST /api/v1/auth/session`
  - `[x]` `DELETE /api/v1/auth/session`
- `[x]` Incident endpoints.
  - `[x]` `POST /api/v1/incidents`
  - `[x]` `GET /api/v1/incidents`
  - `[x]` `GET /api/v1/incidents/{id}`
  - `[x]` `PUT /api/v1/incidents/{id}/status`
  - `[x]` `GET /api/v1/incidents/{id}/history`
  - `[x]` Verify pagination and status filters.
  - `[x]` Verify invalid state transition returns `422`.
- `[x]` Assignment endpoints.
  - `[x]` `POST /api/v1/assignments`
  - `[x]` `DELETE /api/v1/assignments/{id}`
  - `[x]` Verify assignment release updates unit status.
  - `[x]` Add/verify Responder own-assignment API behavior.
  - `[x]` Add/verify Hospital Admin patient accept/reject behavior.
- `[x]` Resource endpoints.
  - `[x]` `GET /api/v1/resources`
  - `[x]` `GET /api/v1/resources/{id}/availability`
  - `[x]` `PUT /api/v1/resources/{id}/availability`
  - `[x]` `PUT /api/v1/resources/{id}/reserve`
  - `[x]` `GET /api/v1/resources/proximity`
  - `[x]` Verify proximity is never cached.
  - `[x]` Verify responder availability toggle behavior.
- `[x]` Dashboard, audit, health.
  - `[x]` `GET /api/v1/dashboard/summary`
  - `[x]` priority queue view/top-N behavior.
  - `[x]` `GET /api/v1/audit/logs`
  - `[x]` `GET /api/v1/health`
  - `[x]` Verify audit logs are never cached.
  - `[x]` Verify health checks Redis and Supabase reachability.

## 6. Redis and Redisson Tasks

- `[x]` Shared Redisson infrastructure.
  - `[x]` Single `RedissonClient`.
  - `[x]` Redis key constants.
  - `[x]` Confirm no direct Jedis/Lettuce usage.
- `[x]` Locks.
  - `[x]` `lock:unit:{unitId}`.
  - `[x]` `lock:resource:{id}`.
  - `[x]` Verify/complete `lock:bed:{hospitalId}` for hospital bed reservation.
  - `[x]` Capture "Lock acquired" and "Lock released" logs.
- `[x]` Cache.
  - `[x]` `incidents:active` TTL 300.
  - `[x]` `incident:{id}` TTL 180.
  - `[x]` `resources:availability:{emirate}` TTL 60.
  - `[x]` `resource:{id}:avail` TTL 60.
  - `[x]` `dashboard:summary` TTL 120.
  - `[x]` Verify all required evictions.
  - `[x]` Verify no audit/session/proximity caching.
- `[x]` Rate limit, blacklist, priority queue.
  - `[x]` `RRateLimiter`.
  - `[x]` `RSet "blacklist:ips"`.
  - `[x]` `RScoredSortedSet incident:priority:queue`.
  - `[x]` Verify resolved incidents are removed from priority queue.

## 7. Bonus Feature Tasks

- `[x]` AI triage engine.
  - `[x]` `POST /api/v1/triage/analyze`.
  - `[x]` first-match-wins keyword rules.
  - `[x]` structured response fields.
  - `[x]` Verify all keyword rows from SRS.
  - `[x]` Verify accepted/overridden audit actions.
- `[x]` PostGIS proximity.
  - `[x]` `ST_DWithin` / `ST_Distance`.
  - `[x]` ETA calculation.
  - `[x]` Verify parameterized query and type filter.
  - `[x]` Verify sort by distance and limit 10.
- `[x]` Redis priority dispatch queue.
  - `[x]` weighted scoring.
  - `[x]` Demo live reordering for multiple criticalities.

## 8. Frontend Tasks

> Frontend synced to the conformant backend contract; `npm run build` passes.
> Remaining items are mostly demo/verification, not implementation.

- `[x]` API contract alignment.
  - `[x]` Update API layer to final backend contract: `criticality`, final status values, `POST /triage/analyze`, `PUT /incidents/{id}/status` body.
  - `[x]` Remove stale `severity`/`/triage/evaluate` assumptions (typed `src/types`, `IncidentContext`, pages).
  - `[x]` Vite `import.meta.env` types + `.env`/`.env.example` (publishable key) added; build green.
- `[x]` Auth View.
  - `[x]` Supabase client initialized.
  - `[x]` Complete login + Supabase TOTP UI (challenge/verify flow).
  - `[x]` Call backend session endpoint after Supabase login (`AuthContext`).
- `[x]` Dashboard & Incident List.
  - `[x]` summary metrics (`/dashboard/summary`).
  - `[x]` incident table (rows link to detail).
  - `[x]` status filter chips.
  - `[x]` priority ordering (backend returns priority-sorted list).
  - `[x]` create incident flow (page) — `[x]` could be a modal per SRS wording.
  - `[x]` AI triage prefill (live `/triage/analyze` preview + criticality prefill).
- `[x]` Incident Detail & Action.
  - `[x]` incident details.
  - `[x]` status transition buttons (Start/Resolve, gated by current status).
  - `[x]` proximity unit list (`/resources/proximity`).
  - `[x]` assign button.
  - `[x]` visible `409` conflict message.
- `[x]` Admin / Logs.
  - `[x]` audit log table for Dispatcher/System Admin (paginated).
  - `[x]` Hospital Admin availability editor (editable beds/ICU, PUT availability).
  - `[x]` System Admin IP blacklist/cache controls UI ("System Controls" tab: block/unblock IP + purge caches, wired to `/admin`).
  - `[x]` role-based tab/action visibility (normalized role from JWT).
- `[x]` Read-Only UX.
  - `[x]` allow list/dashboard views.
  - `[x]` disable mutation actions (dispatcher-only buttons hidden).
  - `[x]` explicit `403` denial toast if a mutation is attempted directly. (global axios response interceptor -> `Toaster`; also handles 429)

## 9. Swagger and API Documentation

- `[x]` SpringDoc setup.
  - `[x]` Swagger UI endpoint.
  - `[x]` OpenAPI JSON endpoint.
  - `[x]` Add/verify `@Operation` on every endpoint. (Auth + Audit controllers added; all 9 controllers covered)
  - `[x]` Add/verify DTO `@Schema` descriptions and examples.
  - `[x]` Add/verify `@ApiResponse` for `200/201`, `400`, `401`, `403`, `409`, `422`, `429`. (global `OpenApiCustomizer` injects 400/401/403/409/422/429 on every op; success codes per `@Operation`)
  - `[x]` Add Bearer JWT `SecurityRequirement` on protected endpoints. (`@SecurityRequirement(bearerAuth)` on all protected controllers + global security item)
  - `[x]` Export OpenAPI JSON for report. (App is running; successfully generated)

## 10. Testing and Evidence

- `[x]` Concurrency evidence.
- `[x]` Security evidence. (MockMvc integration test suite successfully executes and validates all criteria: expired JWTs, wrong roles, path traversals, CORS, IP blacklisting)
- `[x]` Cache evidence.

## 11. Reports and Deliverables

- `[x]` Full Java project final build with Java 21. (`./mvnw clean package` -> BUILD SUCCESS, bytecode v65)
- `[ ]` TypeScript frontend using real Supabase Auth and backend APIs.
- `[ ]` Demo video.
- `[ ]` System architecture + DB ERD report.
- `[ ]` Redis key inventory and analytical Redis section.
- `[ ]` Auth flow diagram.
- `[ ]` Swagger/OpenAPI documentation report.
- `[ ]` Concurrency test report.
- `[ ]` Security test report.
- `[ ]` Cache validation report.
- `[ ]` Self-evaluation report.
