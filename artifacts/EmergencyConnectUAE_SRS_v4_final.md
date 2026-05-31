

**EmergencyConnectUAE™**

Software Requirements Specification

*Version 4.0*

CSC408 – Distributed Information Systems  |  Assignment 3  |  Spring 2026

Abu Dhabi University  ·  Department of Computer Science and Information Technology

*Java 21 / Spring Boot 3  ·  Redisson (Redis)  ·  Supabase (PostgreSQL \+ Auth \+ PostGIS)  ·  TypeScript*

**Bonus: AI Triage Engine  ·  PostGIS Proximity Search  ·  Redis Priority Queue**

Deadline: 31 May 2026 at 23:59  |  Demo Days: 1–4 June 2026

# **1\.  Project Purpose**

EmergencyConnectUAE™ is a distributed emergency coordination platform supporting critical public safety operations in the UAE. It enables secure, real-time coordination between dispatchers, emergency units, and hospitals, and demonstrates the following core distributed systems concepts:

* **Hybrid Authentication:** Supabase Auth for identity management (JWT issuance \+ TOTP MFA), with Redis for token revocation and fast session lookup.

* **Concurrency Control:** Redisson distributed locks (RLock) to prevent race conditions during concurrent resource assignment.

* **Caching:** Redis caching of high-read, low-write data to reduce Supabase query load and improve response latency.

* **Geospatial Processing:** PostGIS ST\_DWithin and ST\_Distance queries for proximity-based resource allocation.

*The system runs entirely on localhost. Two roles can be demonstrated simultaneously using two different browsers (e.g., Chrome as Dispatcher, Firefox as Responder) on the same machine, or on two devices sharing the same LAN with the backend’s LAN IP replacing localhost in the frontend config.*

# **2\.  System Scope**

Four integrated components running on localhost:

* **TypeScript Frontend** — four merged screens covering auth, dashboards, incident management, and admin views.

* **Java Spring Boot Backend** — REST API with business logic, JWT validation, RBAC, and Redisson integration.

* **Supabase** — PostgreSQL database with PostGIS, plus Auth service for JWT issuance and TOTP MFA.

* **Redis via Redisson** — distributed locks (RLock), session metadata (RMapCache), caching, and rate limiting (RRateLimiter).

Core functionality: incident management, unit assignment, resource availability, audit logging, RBAC, and concurrency-safe reservation.

Bonus functionality: rule-based AI triage engine, PostGIS proximity search, Redis priority dispatch queue.

# **3\.  System Architecture**

## **3.1  Authentication Strategy**

Identity management is fully delegated to Supabase Auth to eliminate boilerplate. Spring Boot handles only validation and enforcement.

Step 1:  User logs in via Supabase Auth client (email \+ password)  
         Supabase validates credentials, issues RS256-signed JWT  
         JWT contains: sub (userId), email, app\_metadata.role, amr (auth methods)  
   
Step 2:  Dispatcher / System Admin must complete Supabase TOTP MFA  
         Supabase appends  amr: \[{"method":"totp"}\]  to the JWT on success  
   
Step 3:  Frontend calls POST /api/v1/auth/session  (Spring Boot)  
         Spring Boot validates JWT via Supabase JWKS endpoint  
         Writes thin RMapCache session:  session:{jti}  
           → { userId, role, ipAddress, expiresAt }  
   
Step 4:  All API calls carry JWT in Authorization: Bearer header  
         JwtValidationFilter: validates signature \+ checks session:{jti} exists in Redis  
         Missing session key → 401  (token revoked / user logged out)  
         Reads amr claim to verify MFA for protected roles  
   
Step 5:  DELETE /api/v1/auth/session  (logout)  
         Redisson RMapCache key deleted → JWT effectively dead before natural expiry

*The Redis session exists for exactly two reasons: (1) real token revocation on logout — without it, a valid JWT cannot be invalidated before it expires; (2) fast role lookup without re-parsing the JWT on every request. MFA verification is handled by checking the amr claim in the JWT itself, not a custom Redis flag.*

## **3.2  Backend**

* Java 21 / Spring Boot 3 running on localhost:8080

* Stateless — no in-process HTTP session; all context from JWT \+ Redis session lookup

* Layered architecture: Controller → Service → ServiceImpl → Repository → Model

* **Spatial mapping:** org.hibernate.orm:hibernate-spatial maps PostGIS GEOGRAPHY columns to org.locationtech.jts.geom.Point. Entity fields annotated @Column(columnDefinition \= "geography(Point,4326)") to ensure correct type binding.

* **Redis integration:** All Redis interactions (locks, session, cache, rate limiting) go through a single RedissonClient bean. No direct Jedis or Lettuce usage.

## **3.3  Supabase Layer**

* PostgreSQL database for all application data

* PostGIS extension enabled; all location columns use GEOGRAPHY(POINT, 4326\) with GIST spatial indexes

* Supabase Auth manages user accounts, password hashing, JWT signing (RS256), TOTP secret storage, and MFA enforcement

* Role stored in app\_metadata.role; readable as a claim in the JWT

* Soft deletes only — is\_deleted flag on all tables; no hard deletes

## **3.4  Redis Layer (Redisson)**

* **RLock** — distributed lock for unit assignment and resource reservation (Redlock pattern, automatic lock renewal via watchdog)

* **RMapCache** — session metadata store with per-entry TTL matching JWT expiry

* **Spring Cache \+ Redisson** — @Cacheable / @CacheEvict backed by Redisson for incident and resource caching

* **RRateLimiter** — rate limiting per user/IP without requiring a separate Bucket4j dependency

* **RSet** — IP blacklist for O(1) lookup at filter entry

* **RScoredSortedSet** — priority dispatch queue for the bonus incident ordering feature

# **4\.  User Roles and RBAC**

Roles are set in Supabase app\_metadata and carried as claims in the JWT. Spring Security @PreAuthorize annotations enforce access per endpoint. All denials are written to audit\_logs with result: DENIED. The table below shows all five supported roles.

| Role | MFA | Key capabilities | Restricted from |
| :---- | :---- | :---- | :---- |
| Dispatcher | Yes — Supabase TOTP(amr claim checked) | Create incidents, assign units, view all audit logs, manage IP blacklists, use AI triage, view priority queue | User management, system configuration |
| Responder | No | View own assignments, update incident status (IN\_PROGRESS / RESOLVED), mark self available or unavailable | Audit logs, resource reservation, other responders’ data |
| Hospital Admin | No | Update bed and ICU availability for own hospital, accept or reject patient assignments | Audit logs, unit dispatch, other hospitals’ data |
| System Admin | Yes — Supabase TOTP(amr claim checked) | Manage Supabase users, view all audit logs, purge Redis cache, manage IP blacklist/whitelist, health checks | Incident status transitions |
| Read-Only | No | View paginated incident list, dashboard summary, resource availability | All mutation endpoints, audit logs, assignments |

*Hospital Admin is included because the assignment data model explicitly requires it (dispatcher, responder, hospital admin). Minimum viable implementation: role exists in Supabase, JWT carries the claim, and resource availability PUT endpoints check for it. No separate frontend dashboard required.*

*Read-Only is retained as the cleanest RBAC demonstration: the same GET /incidents endpoint returns data for any role, but POST /assignments returns 403 for Read-Only regardless of how the request is constructed.*

# **5\.  Functional Requirements**

## **5.1  Authentication and Session Management**

* Supabase Auth owns login, registration, password hashing, JWT issuance, and TOTP MFA

* Spring Boot validates JWT signatures via the Supabase JWKS endpoint (no raw secret needed)

* Redis session written on first authenticated backend call; deleted on logout

* amr claim in JWT used to verify MFA completion for Dispatcher and System Admin roles

POST    /api/v1/auth/session    — validate JWT, write RMapCache session (after Supabase login)  
DELETE  /api/v1/auth/session    — delete session key → token revoked immediately

## **5.2  Incident Management**

Dispatchers create incidents based on incoming 999 calls. The public does not submit incidents directly through the app.

* Incidents follow the state machine: OPEN → IN\_PROGRESS → RESOLVED. Backwards transitions are rejected with 422\.

* All list endpoints are paginated. Full datasets are never returned uncontrolled.

* Active incident list and individual incident detail are Redis-cached.

* Any status update immediately evicts affected cache entries.

POST  /api/v1/incidents               — Dispatcher creates incident (triggers AI triage)  
GET   /api/v1/incidents               — paginated list; filterable by status  \[cached\]  
GET   /api/v1/incidents/{id}          — incident detail                        \[cached\]  
PUT   /api/v1/incidents/{id}/status   — status transition                      \[evicts cache\]  
GET   /api/v1/incidents/{id}/history  — paginated audit trail                  \[never cached\]

## **5.3  Emergency Resource Management**

Unit assignment is the primary critical section. A Redisson RLock is acquired before any write and released immediately after. Two concurrent requests for the same unit can never both succeed.

POST    /api/v1/assignments                   — assign unit (RLock acquired)  
DELETE  /api/v1/assignments/{id}              — release assignment and unit  
   
GET     /api/v1/resources                     — paginated availability list  \[cached\]  
GET     /api/v1/resources/{id}/availability   — single resource status       \[cached\]  
PUT     /api/v1/resources/{id}/availability   — update counts               \[evicts cache\]  
PUT     /api/v1/resources/{id}/reserve        — reserve resource             \[RLock acquired\]  
GET     /api/v1/resources/proximity           — PostGIS nearest search       \[never cached\]

## **5.4  Dashboard and Audit**

* Dashboard summary is Redis-cached and invalidated on any status or availability change

* Audit logs are never cached — always read directly from Supabase

* Health endpoint confirms Redis connectivity (Redisson ping) and Supabase reachability

GET  /api/v1/dashboard/summary   — metrics by emirate / status  \[cached 120 s\]  
GET  /api/v1/audit/logs          — paginated audit trail        \[never cached\]  
GET  /api/v1/health              — system health check

# **6\.  Bonus Requirements  \[+2.5 marks\]**

*Bonus marks are awarded only after all mandatory requirements are satisfied. All three features must be demonstrated during the demo.*

## **6.1  AI Triage Engine (Simulated)**

A deterministic rule-based service that parses incident descriptions and returns structured recommendations. No external API or ML model. Rules evaluated top-to-bottom; first match wins.

POST  /api/v1/triage/analyze  
  Request:  { "description": "Car accident on Sheikh Zayed Road, 3 vehicles involved" }  
  Response: {  
    "criticality":             "HIGH",  
    "confidence":              0.85,  
    "recommendedUnits":        \["AMBULANCE", "POLICE"\],  
    "recommendedHospitalTier": "TRAUMA",  
    "matchedKeywords":         \["accident", "road"\],  
    "dispatchCount":           2  
  }

| Keyword patterns (first match wins) | Criticality | Units | Hospital tier |
| :---- | :---- | :---- | :---- |
| cardiac, heart attack, chest pain, MI, myocardial | CRITICAL | Ambulance \+ Cardiac Unit | Tertiary cardiac (ICU) |
| stroke, CVA, facial droop, slurred speech | CRITICAL | Ambulance \+ Advanced Paramedic | Stroke-capable tertiary |
| fire, smoke, blaze, explosion, burning building | HIGH | Fire Brigade \+ Ambulance | Nearest A\&E \+ burns unit |
| accident, crash, collision, RTA, road traffic | HIGH | Ambulance \+ Police | Nearest trauma hospital |
| drowning, water rescue, flood | HIGH | Marine Rescue \+ Ambulance | Nearest A\&E |
| fall, fracture, broken bone, injury, unconscious | MEDIUM | Ambulance | Nearest general hospital |
| missing person, lost child, welfare check | LOW | Police Unit | None |
| (no keyword match) | MEDIUM | General Ambulance | Nearest general hospital |

Dispatcher may accept or override the recommendation. Both outcomes written to audit\_logs with action TRIAGE\_ACCEPTED or TRIAGE\_OVERRIDDEN.

## **6.2  PostGIS Proximity Search**

Returns nearby available units and hospitals sorted by distance from a GPS coordinate. Not cached because GPS positions change continuously.

GET  /api/v1/resources/proximity?lat={lat}\&lng={lng}\&radius={m}\&type={TYPE}  
   
\-- Parameterized query (no string interpolation):  
SELECT id, name, type, status,  
  ST\_Distance(location::geography,  
    ST\_MakePoint(:lng, :lat)::geography) AS distance\_m  
FROM emergency\_units  
WHERE status \= 'AVAILABLE'  
  AND ST\_DWithin(location::geography,  
        ST\_MakePoint(:lng, :lat)::geography, :radius)  
  AND (:type \= 'ALL' OR type \= :type)  
ORDER BY distance\_m ASC  
LIMIT 10;  
   
\-- Response per item: id, name, type, status, distanceMetres, estimatedArrivalMinutes  
\-- estimatedArrivalMinutes \= distanceMetres / avgSpeedMs  
\-- Speeds: AMBULANCE 60 km/h, HELICOPTER 200 km/h, POLICE 80 km/h, FIRE 50 km/h

## **6.3  Redis Priority Dispatch Queue**

* **Key:** incident:priority:queue  (Redisson RScoredSortedSet)

* Score \= criticality weight × incident age in seconds

* Weights: CRITICAL=100, HIGH=75, MEDIUM=50, LOW=25

* Older high-criticality incidents automatically rise above newer lower-priority ones

* **ZREM on status transition to RESOLVED.** Dashboard fetches top-N from sorted set for the priority view.

*This is the data source for the priority incident order shown on the Dashboard & Incident List screen. The grader should see incidents re-ordering live as new ones are created with different criticality levels.*

# **7\.  Data Model**

All tables use UUID primary keys, created\_at / updated\_at timestamps, and soft deletion via is\_deleted. No hard deletes are performed anywhere in the system. Spatial fields are annotated @Column(columnDefinition \= "geography(Point,4326)") in the entity class and mapped to org.locationtech.jts.geom.Point via hibernate-spatial.

## **7.1  incidents**

id           UUID          PRIMARY KEY  DEFAULT gen\_random\_uuid()  
description  TEXT          NOT NULL  
status       TEXT          NOT NULL     CHECK (status IN ('OPEN','IN\_PROGRESS','RESOLVED'))  
criticality  TEXT                       CHECK (criticality IN ('CRITICAL','HIGH','MEDIUM','LOW'))  
location     GEOGRAPHY(POINT,4326)      \-- GIST indexed; mapped to JTS Point  
reported\_by  UUID          REFERENCES users(id)  
created\_at   TIMESTAMPTZ   DEFAULT now()  
updated\_at   TIMESTAMPTZ   DEFAULT now()  
is\_deleted   BOOLEAN       DEFAULT false

## **7.2  emergency\_units**

id           UUID          PRIMARY KEY  DEFAULT gen\_random\_uuid()  
type         TEXT          NOT NULL     CHECK (type IN ('AMBULANCE','FIRE','POLICE','HELICOPTER'))  
status       TEXT          NOT NULL     CHECK (status IN ('AVAILABLE','DISPATCHED','OFFLINE'))  
home\_station TEXT  
location     GEOGRAPHY(POINT,4326)      \-- GIST indexed; mapped to JTS Point  
hospital\_id  UUID          REFERENCES hospitals(id)  
is\_deleted   BOOLEAN       DEFAULT false

## **7.3  hospitals**

id              UUID        PRIMARY KEY  DEFAULT gen\_random\_uuid()  
name            TEXT        NOT NULL  
emirate         TEXT        NOT NULL  
location        GEOGRAPHY(POINT,4326)    \-- GIST indexed; mapped to JTS Point  
total\_beds      INT         NOT NULL  
available\_beds  INT         NOT NULL  
icu\_available   INT         NOT NULL  
is\_deleted      BOOLEAN     DEFAULT false

## **7.4  assignments**

id           UUID          PRIMARY KEY  DEFAULT gen\_random\_uuid()  
incident\_id  UUID          NOT NULL     REFERENCES incidents(id)  
unit\_id      UUID          NOT NULL     REFERENCES emergency\_units(id)  
assigned\_by  UUID          NOT NULL     REFERENCES users(id)  
assigned\_at  TIMESTAMPTZ   NOT NULL     DEFAULT now()  
released\_at  TIMESTAMPTZ               \-- NULL \= currently active assignment

## **7.5  users**

id         UUID          PRIMARY KEY   \-- matches Supabase Auth user id (sub claim)  
email      TEXT          UNIQUE NOT NULL  
role       TEXT          NOT NULL  
is\_active  BOOLEAN       DEFAULT true  
created\_at TIMESTAMPTZ   DEFAULT now()  
   
\-- password\_hash and mfa\_secret are owned entirely by Supabase Auth.  
\-- This table stores only the application-level user profile.

## **7.6  audit\_logs**

id            UUID          PRIMARY KEY  DEFAULT gen\_random\_uuid()  
user\_id       UUID          REFERENCES users(id)  
action        TEXT          NOT NULL  
              \-- e.g. INCIDENT\_CREATED, STATUS\_UPDATED, UNIT\_ASSIGNED,  
              \--      TRIAGE\_ACCEPTED, TRIAGE\_OVERRIDDEN, ACCESS\_DENIED  
resource\_type TEXT          NOT NULL     \-- INCIDENT | ASSIGNMENT | RESOURCE | SESSION  
resource\_id   UUID  
timestamp     TIMESTAMPTZ   NOT NULL     DEFAULT now()  
ip\_address    TEXT  
result        TEXT          NOT NULL     CHECK (result IN ('SUCCESS','DENIED'))

# **8\.  Redis Requirements (Redisson)**

All Redis interactions go through a single RedissonClient bean configured in RedissonConfig.java. No direct Jedis or Lettuce usage. All three mandatory roles must be demonstrated during the demo.

## **8.1  Distributed Locks (RLock) — MANDATORY**

Protects critical write operations from concurrent access. Uses Redisson's RLock which implements the Redlock pattern automatically, including watchdog-based lock renewal.

// AssignmentServiceImpl.java — lab pattern ServiceImpl  
@Override  
public AssignmentResponse assignUnit(AssignmentRequest request) {  
    String lockKey \= "lock:unit:" \+ request.getUnitId();  
    RLock lock \= redissonClient.getLock(lockKey);  
    try {  
        // waitTime=5s (how long to retry), leaseTime=10s (auto-release)  
        if (\!lock.tryLock(5, 10, TimeUnit.SECONDS)) {  
            throw new ResourceLockedException("Unit is currently being assigned");  
        }  
        // Critical section: check availability \+ write assignment  
        return doAssign(request);  
    } finally {  
        if (lock.isHeldByCurrentThread()) lock.unlock();  
    }  
}

| Lock key | Lease TTL | Protected endpoint | On contention |
| :---- | :---- | :---- | :---- |
| lock:unit:{unitId} | 10 s | POST /assignments | 409 Conflict — {"error":"RESOURCE\_LOCKED"} |
| lock:resource:{id} | 10 s | PUT /resources/{id}/reserve | 409 Conflict — {"error":"RESOURCE\_LOCKED"} |
| lock:bed:{hospitalId} | 10 s | Hospital bed reservation | 409 Conflict — {"error":"RESOURCE\_LOCKED"} |

**When NOT to use locks:** read-only operations, operations with no shared mutable state, and high-throughput flows where lock wait time creates unacceptable latency. Use optimistic concurrency (version columns) in those cases.

## **8.2  Caching (Spring Cache \+ Redisson) — MANDATORY**

High-read, low-write data is cached to reduce Supabase round-trips. Implementation: @Cacheable and @CacheEvict annotations on ServiceImpl methods, backed by Redisson through the Spring Cache abstraction.

| Cache key | TTL | @CacheEvict trigger |
| :---- | :---- | :---- |
| incidents:active | 300 s | POST /incidents  or  PUT /incidents/{id}/status |
| incident:{id} | 180 s | PUT /incidents/{id}/status on that specific id |
| resources:availability:{emirate} | 60 s | PUT reserve, PUT availability, DELETE assignment |
| resource:{id}:avail | 60 s | PUT /resources/{id}/availability |
| dashboard:summary | 120 s | Any status or availability change |

**When NOT to cache:** audit logs (must always be current), session endpoints, PostGIS proximity queries (GPS positions change faster than any useful TTL), and availability counts used for incoming critical patients (stale safety data).

## **8.3  Session Metadata (RMapCache) — MANDATORY**

A thin per-token hash stored in Redisson RMapCache. Provides token revocation on logout — something a self-contained JWT cannot do without a server-side record.

Key:    session:{jti}          TTL: matches JWT expiry (default 3600 s)  
   
Hash fields (RMapCache entries):  
  userId     — UUID (matches JWT sub claim)  
  role       — ROLE\_DISPATCHER | ROLE\_RESPONDER | ROLE\_HOSPITAL\_ADMIN | ...  
  ipAddress  — client IP at session creation (for audit correlation)  
  expiresAt  — Unix epoch ms  
   
On DELETE /api/v1/auth/session:  
  redissonClient.getMapCache("session").remove(jti)  
  → JWT is now refused by JwtValidationFilter even if still cryptographically valid

**MFA verification:** Checked via the amr claim in the JWT itself (not a Redis flag). If amr does not contain {"method":"totp"}, the request is rejected with 403 for MFA-required roles.

**When NOT appropriate:** data residency regulations that prevent session data from being stored on the Redis cluster’s host, or when Redis has no HA configuration and session loss on failure is unacceptable.

## **8.4  Rate Limiting (RRateLimiter)**

Redisson's RRateLimiter replaces Bucket4j, requiring no additional dependency since Redisson is already imported.

RRateLimiter limiter \= redissonClient.getRateLimiter("rl:" \+ userId);  
limiter.trySetRate(RateType.OVERALL, 60, 1, RateIntervalUnit.MINUTES);  
if (\!limiter.tryAcquire()) {  
    throw new RateLimitExceededException(); // → 429 Too Many Requests  
}  
   
// Login endpoint: 5 attempts / minute per IP  
RRateLimiter loginLimiter \= redissonClient.getRateLimiter("rl:login:" \+ ipAddress);  
loginLimiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.MINUTES);

## **8.5  Unified Request Lifecycle**

1\.  Request arrives with JWT in Authorization: Bearer header  
2\.  RateLimitFilter: checks RRateLimiter for userId/IP  →  429 if exceeded  
3\.  JwtValidationFilter: validates signature via Supabase JWKS endpoint  
4\.  Filter: checks RSet "blacklist:ips" for client IP  →  403 if present  
5\.  Filter: looks up RMapCache "session:{jti}"  →  401 if absent (logged out)  
6\.  Filter: extracts role from session; checks amr claim for MFA-required roles  
7\.  @PreAuthorize enforces RBAC  →  403 if role insufficient  
   
    For READ endpoints:  
8a. @Cacheable lookup  →  cache hit returns immediately (no Supabase query)  
                       →  cache miss queries Supabase, populates cache, returns result  
   
    For WRITE endpoints on shared resources:  
8b. RLock.tryLock()  →  409 if contended after retries  
    Mutate Supabase  →  @CacheEvict clears affected keys  →  RLock.unlock()  
   
9\.  Audit log written for every request (success or denial)

# **9\.  API Requirements**

* Base path: /api/v1/ for all endpoints

* All requests and responses use JSON

* Stateless — no in-process HTTP session; all context from JWT \+ Redis session lookup

* Resource-oriented URIs: nouns only, no verbs. /incidents not /getIncident.

* **HTTP verbs:** GET (read), POST (create), PUT (update), DELETE (remove)

* **Status codes:** 200 OK, 201 Created, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict (lock), 422 Unprocessable Entity (validation), 429 Too Many Requests

### **Pagination**

Every endpoint returning a list must support pagination. Full uncontrolled responses are never returned.

Request:   ?page=0\&size=20   (page: 0-indexed, default 0; size: default 20, max 100\)  
Response:  { data\[\], page, size, totalElements, totalPages, last }

# **10\.  Security Requirements**

## **10.1  JWT Validation (JWKS)**

* Spring Boot configures a JwtDecoder bean pointing at the Supabase JWKS endpoint:

${SUPABASE\_JWKS\_URI}  \=  https://\<project\>.supabase.co/auth/v1/.well-known/jwks.json

* RS256 public key fetched and cached by Spring Security automatically

* Expired, tampered, or missing tokens return 401\. No raw JWT secret stored in the application.

## **10.2  MFA Enforcement**

* Supabase Auth handles TOTP secret generation, QR code delivery, and 30-second code verification

* **Backend check:** JwtValidationFilter reads the amr claim from the validated JWT

// Claim present after Supabase TOTP completion:  
amr: \[{"method": "totp", "timestamp": 1748000000}\]  
   
// Filter logic for MFA-required roles:  
boolean mfaDone \= jwtAmr.stream()  
    .anyMatch(m \-\> "totp".equals(m.get("method")));  
if (\!mfaDone) throw new MfaRequiredException(); // → 403

* MFA bypass is not permitted in any environment, including development

## **10.3  Input Validation**

* Jakarta Bean Validation on all DTO fields: @Valid, @NotNull, @Size, @Pattern, @Email

* Validation failure returns 422 with structured field-level error map from GlobalExceptionHandler

* All database queries use JPA parameterized statements — no raw string interpolation

* ../, %2E%2E, and null bytes in any path variable or query parameter return 400 immediately

## **10.4  IP Blacklist**

* **Storage:** Redisson RSet "blacklist:ips". O(1) lookup at filter entry before any other processing.

* IPs generating 10+ 401/403 responses within any 5-minute window are auto-added

* System Admin can add or remove entries manually via the admin API

* Internal endpoints (/health, /actuator/\*\*) restricted to localhost CIDR

## **10.5  CORS**

* Allowed origins configured via environment variable — no wildcard (\*)

${CORS\_ALLOWED\_ORIGINS}  \=  http://localhost:3000  (or LAN IP for two-device demo)

* Allowed methods: GET, POST, PUT, DELETE, OPTIONS

## **10.6  Secure Configuration**

* No secrets, keys, or credentials in source code or version control

${SUPABASE\_JWKS\_URI}   ${REDIS\_ADDRESS}   ${REDIS\_PASSWORD}  
${DB\_URL}              ${DB\_PASSWORD}      ${CORS\_ALLOWED\_ORIGINS}

* .env excluded from version control via .gitignore

## **10.7  HTTP Security Headers**

X-Content-Type-Options:    nosniff  
X-Frame-Options:           DENY  
Content-Security-Policy:   default-src 'self'  
Strict-Transport-Security: max-age=31536000  
Referrer-Policy:           no-referrer

# **11\.  Concurrency Requirements**

This is a mandatory graded category. The demo must show both the failure mode (no locks) and the correct mode (with locks).

### **Required Two-Phase Test (JMeter or k6)**

* **Phase 1 — locks disabled:** Send 50 concurrent POST /assignments requests all targeting the same unitId. Expected result: multiple 201 Created responses or corrupted unit state. This proves the race condition exists without locking.

* **Phase 2 — locks enabled:** Same 50 concurrent requests. Expected result: exactly 1 returns 201 Created; all other 49 return 409 Conflict with {"error":"RESOURCE\_LOCKED"}. The unit is assigned to exactly one incident and its status is DISPATCHED exactly once.

*JMeter/k6 summary screenshots from both phases, plus application log excerpts showing "Lock acquired" and "Lock released" events, must be included in the report. A passing test without evidence does not satisfy the rubric.*

# **12\.  Frontend Requirements**

TypeScript application (React or Vue) running on localhost:3000. Uses the Supabase JS client SDK for auth. All data from Spring Boot REST API. Local component state is sufficient — no Redux or global state manager required.

*All screens must use real API data. The Supabase login and MFA screens provided by the Supabase Auth UI component count as real integration. No mock-only interfaces accepted.*

| Screen | What it covers | Rubric criteria demonstrated |
| :---- | :---- | :---- |
| 1\.  Auth View(Login \+ MFA) | Supabase Auth UI component handles email/password entry and TOTP code verification. On success, calls POST /api/v1/auth/session to write the Redis session, then redirects to Dashboard. | JWT flow, MFA enforcement, Redis session creation |
| 2\.  Dashboard & Incident List | Top row: summary metrics (active incidents, available units). Below: paginated incident table with status filter chips. Incidents ordered by Redis priority queue score. Create Incident button opens a modal that also calls POST /api/v1/triage/analyze and pre-fills the severity field. | Caching (list is Redis-cached), priority queue, AI triage, pagination |
| 3\.  Incident Detail & Action | Clicking a row opens this view. Left panel: incident info \+ status-transition buttons (Start / Resolve). Right panel: available units list (from proximity search) and Assign button. Assign button triggers POST /api/v1/assignments which acquires the Redisson lock. 409 response shown as a user-facing error. | Distributed lock demo (409 visible), PostGIS proximity, cache eviction on status change |
| 4\.  Admin / Logs | Tab 1 (Dispatcher \+ System Admin): paginated audit log table. Tab 2 (Hospital Admin): resource availability table with editable bed and ICU counts. Tab visibility controlled by JWT role claim. | RBAC demo, audit logging, Hospital Admin role, resource cache invalidation |

# **13\.  Backend Package Structure**

Uses a feature-based (domain-based) layout. Each domain package owns its own Controller, Service interface, ServiceImpl, Repository, Model, and DTOs — mirroring the structure of the reference project shown in the lab environment. Cross-cutting concerns (security, config, exception handling, utilities) remain in their own top-level packages.

*Inside each domain, the same four-step lab pattern still applies: Service interface → ServiceImpl → Controller → Repository. The only difference from the flat layout is that all four layers live inside the same feature folder instead of in separate top-level folders.*

com.emergencyconnectuae  
├── auth                              \-- authentication and session management  
│   ├── AuthController.java           \-- POST/DELETE /api/v1/auth/session  
│   ├── AuthService.java              \-- (interface)  
│   ├── AuthServiceImpl.java          \-- writes/deletes RMapCache session  
│   ├── User.java                     \-- @Entity; id matches Supabase Auth sub  
│   ├── UserRepository.java           \-- extends JpaRepository\<User, UUID\>  
│   └── dto  
│       ├── SessionRequest.java  
│       └── SessionResponse.java  
├── incident                           \-- incident lifecycle  
│   ├── IncidentController.java        \-- /api/v1/incidents/\*\*  
│   ├── IncidentService.java           \-- (interface)  
│   ├── IncidentServiceImpl.java       \-- @Cacheable, @CacheEvict, priority queue  
│   ├── Incident.java                  \-- @Entity; location: JTS Point  
│   ├── IncidentRepository.java        \-- extends JpaRepository\<Incident, UUID\>  
│   └── dto  
│       ├── CreateIncidentRequest.java  
│       ├── UpdateStatusRequest.java  
│       └── IncidentResponse.java  
├── assignment                         \-- unit-to-incident assignment (critical section)  
│   ├── AssignmentController.java      \-- /api/v1/assignments/\*\*  
│   ├── AssignmentService.java         \-- (interface)  
│   ├── AssignmentServiceImpl.java     \-- RLock acquired before every write  
│   ├── Assignment.java                \-- @Entity  
│   ├── AssignmentRepository.java      \-- extends JpaRepository\<Assignment, UUID\>  
│   └── dto  
│       ├── AssignmentRequest.java  
│       └── AssignmentResponse.java  
├── resource                           \-- emergency units and hospitals  
│   ├── ResourceController.java        \-- /api/v1/resources/\*\*  (incl. /proximity)  
│   ├── ResourceService.java           \-- (interface)  
│   ├── ResourceServiceImpl.java       \-- RLock on reserve; @Cacheable on reads  
│   ├── EmergencyUnit.java             \-- @Entity; location: JTS Point  
│   ├── Hospital.java                  \-- @Entity; location: JTS Point  
│   ├── ResourceRepository.java        \-- @Query with ST\_DWithin / ST\_Distance  
│   ├── HospitalRepository.java        \-- extends JpaRepository\<Hospital, UUID\>  
│   └── dto  
│       ├── ResourceAvailabilityRequest.java  
│       ├── ResourceResponse.java  
│       └── ProximityResponse.java  
├── dashboard                          \-- summary metrics  
│   ├── DashboardController.java       \-- /api/v1/dashboard/\*\*  
│   ├── DashboardService.java          \-- (interface)  
│   ├── DashboardServiceImpl.java      \-- @Cacheable(dashboard:summary, TTL 120 s)  
│   └── dto  
│       └── DashboardSummaryResponse.java  
├── audit                              \-- audit logging and retrieval  
│   ├── AuditController.java           \-- /api/v1/audit/\*\*  
│   ├── AuditService.java              \-- (interface)  
│   ├── AuditServiceImpl.java          \-- writes to audit\_logs; never cached  
│   ├── AuditLog.java                  \-- @Entity  
│   ├── AuditLogRepository.java        \-- extends JpaRepository\<AuditLog, UUID\>  
│   └── dto  
│       └── AuditLogResponse.java  
├── triage                             \-- AI keyword triage engine (bonus)  
│   ├── TriageController.java          \-- /api/v1/triage/\*\*  
│   ├── TriageService.java             \-- (interface)  
│   ├── TriageServiceImpl.java         \-- ordered keyword ruleset; deterministic  
│   └── dto  
│       ├── TriageRequest.java  
│       └── TriageResponse.java  
├── security                           \-- cross-cutting; no domain ownership  
│   ├── JwtValidationFilter.java       \-- validates JWKS \+ checks RMapCache session  
│   ├── RateLimitFilter.java           \-- RRateLimiter per userId / IP  
│   └── SecurityConfig.java            \-- JWKS JwtDecoder, RBAC, filter chain  
├── config                             \-- infrastructure beans  
│   ├── RedissonConfig.java            \-- RedissonClient bean  
│   ├── CacheConfig.java               \-- Spring Cache manager backed by Redisson  
│   ├── SwaggerConfig.java             \-- SpringDoc OpenAPI bean  
│   └── CorsConfig.java                \-- allowed origins from ${CORS\_ALLOWED\_ORIGINS}  
├── exception                          \-- global error handling  
│   ├── GlobalExceptionHandler.java    \-- @ControllerAdvice; maps all exceptions → HTTP  
│   ├── ResourceNotFoundException.java  \-- → 404  
│   ├── ResourceLockedException.java    \-- → 409  
│   ├── MfaRequiredException.java       \-- → 403  
│   └── InvalidStatusTransitionException.java  \-- → 422  
├── common                             \-- shared types used across domains  
│   └── PagedResponse.java             \-- generic paginated envelope \<T\>  
└── util                               \-- stateless helpers  
    ├── RedisCacheKeys.java             \-- all Redis key name constants  
    └── AuditLogger.java                \-- called from every \*ServiceImpl to write audit\_logs  
   
BackendApplication.java                \-- @SpringBootApplication entry point

### **Lab Pattern Inside a Domain Package — Example: assignment/**

The same four-step pattern from the CSC408 labs applies inside every domain folder. The only change from the lab structure is that all four files live together in one package rather than in separate top-level folders.

// assignment/AssignmentService.java  — Step 1: interface (same as lab Service interface)  
public interface AssignmentService {  
    AssignmentResponse assignUnit(AssignmentRequest request);  
    void releaseAssignment(UUID assignmentId);  
}  
   
// assignment/AssignmentServiceImpl.java  — Step 2: implementation  
@Service  
public class AssignmentServiceImpl implements AssignmentService {  
    @Override  
    public AssignmentResponse assignUnit(AssignmentRequest req) {  
        RLock lock \= redissonClient.getLock("lock:unit:" \+ req.getUnitId());  
        try {  
            if (\!lock.tryLock(5, 10, TimeUnit.SECONDS))  
                throw new ResourceLockedException("Unit is currently being assigned");  
            EmergencyUnit unit \= resourceRepository.findById(req.getUnitId())  
                .orElseThrow(() \-\> new ResourceNotFoundException("Unit","id",req.getUnitId()));  
            if (\!"AVAILABLE".equals(unit.getStatus()))  
                throw new ResourceLockedException("Unit is not available");  
            unit.setStatus("DISPATCHED");  
            resourceRepository.save(unit);  
            Assignment saved \= assignmentRepository.save(buildAssignment(req));  
            auditLogger.log("UNIT\_ASSIGNED", "ASSIGNMENT", saved.getId(), "SUCCESS");  
            return mapToResponse(saved);  
        } finally {  
            if (lock.isHeldByCurrentThread()) lock.unlock();  
        }  
    }  
}  
   
// assignment/AssignmentController.java  — Step 3: controller  
@RestController  
@RequestMapping("/api/v1/assignments")  
public class AssignmentController {  
    @PostMapping  
    @PreAuthorize("hasRole('DISPATCHER')")  
    public ResponseEntity\<AssignmentResponse\> assignUnit(  
            @Valid @RequestBody AssignmentRequest request) {  
        return ResponseEntity.status(HttpStatus.CREATED)  
            .body(assignmentService.assignUnit(request));  
    }  
    @DeleteMapping("/{id}")  
    @PreAuthorize("hasRole('DISPATCHER')")  
    public ResponseEntity\<Void\> releaseAssignment(@PathVariable UUID id) {  
        assignmentService.releaseAssignment(id);  
        return ResponseEntity.noContent().build();  
    }  
}  
   
// assignment/AssignmentRepository.java  — Step 4: repository  
public interface AssignmentRepository extends JpaRepository\<Assignment, UUID\> {  
    List\<Assignment\> findByIncidentIdAndReleasedAtIsNull(UUID incidentId);  
    Optional\<Assignment\> findByUnitIdAndReleasedAtIsNull(UUID unitId);  
}

# **14\.  Maven Dependencies (pom.xml)**

All required dependencies. Version numbers are resolved by Spring Boot BOM where applicable.

\<\!-- Core Spring Boot \--\>  
\<dependency\> spring-boot-starter-web \</dependency\>  
\<dependency\> spring-boot-starter-data-jpa \</dependency\>  
\<dependency\> spring-boot-starter-security \</dependency\>  
\<dependency\> spring-boot-starter-validation \</dependency\>  
   
\<\!-- PostGIS / Spatial \--\>  
\<dependency\> org.hibernate.orm:hibernate-spatial \</dependency\>  
\<\!-- Hibernate 6 dialect: org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect \--\>  
   
\<\!-- Redis via Redisson (replaces Bucket4j; covers locks, cache, rate limiting) \--\>  
\<dependency\> org.redisson:redisson-spring-boot-starter:3.27.2 \</dependency\>  
   
\<\!-- JWT validation via Supabase JWKS \--\>  
\<dependency\> spring-boot-starter-oauth2-resource-server \</dependency\>  
\<\!-- Configures JwtDecoder from ${SUPABASE\_JWKS\_URI} automatically \--\>  
   
\<\!-- Supabase / PostgreSQL driver \--\>  
\<dependency\> org.postgresql:postgresql \</dependency\>  
   
\<\!-- API Documentation \--\>  
\<dependency\> org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0 \</dependency\>  
   
\<\!-- JTS geometry (bundled with hibernate-spatial; no separate import needed) \--\>  
\<\!-- org.locationtech.jts:jts-core is a transitive dependency \--\>

# **15\.  Swagger / OpenAPI Documentation**

* SpringDoc OpenAPI auto-generates documentation from @RestController annotations

* **Swagger UI:** http://localhost:8080/swagger-ui.html

* **OpenAPI JSON:** http://localhost:8080/v3/api-docs

Every endpoint must include:

* A clear @Operation(summary \= "...") description

* Request parameter and body schemas (@Schema on DTO fields with description and example values)

* All response status codes documented (@ApiResponse for 200/201, 400, 401, 403, 409, 422, 429\)

* Authentication requirement (SecurityRequirement annotation pointing to Bearer JWT scheme)

# **16\.  Testing Requirements**

## **16.1  Concurrency Test (Mandatory — see Section 11\)**

Two-phase JMeter/k6 test. Phase 1 demonstrates the race condition. Phase 2 demonstrates correctness with locks. Evidence in report.

## **16.2  Security Tests (JUnit 5 \+ MockMvc or Postman)**

* Expired JWT → 401

* Valid JWT with wrong role for endpoint → 403

* Dispatcher JWT where amr claim is absent (no MFA completed) → 403

* Deleted session (after logout) with still-valid JWT → 401

* SQL injection payload in description field → 422, no DB exception in response body

* ../ in any query parameter → 400

* 51st request within 1 minute from same userId → 429 with Retry-After header

* CORS preflight from non-configured origin → no Access-Control-Allow-Origin in response

## **16.3  Cache Validation**

* 10 sequential GET /incidents requests: request 1 triggers Supabase query; requests 2–10 served from Redis

* After PUT /incidents/{id}/status: next GET triggers fresh Supabase query (cache evicted)

* Evidence: Redis MONITOR output or CACHE\_HIT / CACHE\_MISS log lines in application logs

# **17\.  Non-Functional Requirements**

## **Scalability (design intent)**

* Stateless backend: no in-process state means any instance could serve any request

* Redis session: not tied to a single server process; survives rolling restarts

* Pagination: prevents unbounded queries regardless of dataset size

*Running on localhost is the intended deployment for this assignment. These choices satisfy the rubric wording that design choices must support horizontal scalability.*

## **Reliability**

* RLock ensures exactly-one-success on concurrent reservation requests

* Soft deletes preserve referential integrity and audit history

* Eager cache eviction ensures reads never return stale data after a write

## **Performance**

* Cached responses served without Supabase round-trip (≤ 1 ms Redis vs 20–100 ms Supabase)

* PostGIS GIST indexes make proximity queries sub-millisecond at typical dataset sizes

## **Maintainability**

* Lab-consistent four-layer structure throughout — every team member knows the pattern

* All Redis key names as constants in RedisCacheKeys.java — no magic strings

* All exceptions map to HTTP status codes in one place: GlobalExceptionHandler.java

# **18\.  Deliverables Checklist**

*All Java source files must include Section number, Group number, Student IDs, and names of all group members at the top. Missing information: −1 mark penalty.*

| \# | Deliverable | Required | Key notes |
| :---- | :---- | :---- | :---- |
| 1 | Full Java project (Spring Boot source files) | Yes | Every file must have Section no., Group no., Student IDs, and names at the top. |
| 2 | TypeScript frontend application | Yes | Four merged screens. Real Supabase Auth \+ REST API integration. No mock-only views. |
| 3 | Recorded demo video | Yes | Must show: login \+ MFA, dashboard, incident creation with AI triage, unit assignment (including the 409 on second concurrent attempt), status update with cache eviction, audit log. |
| 4 | Report: system architecture \+ DB ERD | Yes | ERD covering all six tables with PostGIS columns marked. Redis key inventory. Auth flow diagram. |
| 5 | Report: Swagger/OpenAPI documentation | Yes | Every endpoint with schemas, authentication, and at least one usage example. |
| 6 | Report: concurrency test results | Yes | Phase 1 race condition evidence \+ Phase 2 lock correctness evidence (screenshots/logs). |
| 7 | Report: security test results | Yes | All eight security scenarios from Section 16.2 with pass/fail evidence. |
| 8 | Report: cache validation results | Yes | CACHE\_HIT / CACHE\_MISS log evidence for hit, miss, and eviction scenarios. |
| 9 | Report: self-evaluation | Yes | Design tradeoffs, known limitations, and concrete improvement suggestions. |
| 10 | Report: Redis section (analytical) | Yes | All three mandatory roles. Includes when NOT to use each. Must be analytical, not descriptive. |

**Submission deadline:** 31 May 2026 at 23:59. Late penalty: −5% per day.

**Demo days:** 1–4 June 2026\. Every group member must individually demonstrate their contribution. Absence on demo day results in zero for that student.