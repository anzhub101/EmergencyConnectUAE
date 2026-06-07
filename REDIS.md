# EmergencyConnectUAE — Redis Integration & Endpoints

This document describes how **EmergencyConnectUAE** uses **Redis** (via the
[Redisson](https://github.com/redisson/redisson) client) and catalogs every API
endpoint backed by a Redis data structure, with the auth each one requires and
how to test it.

---

## 1. Architecture: why Redis *and* Supabase

The backend uses two storage layers with distinct jobs:

| Layer | Role | Holds |
| --- | --- | --- |
| **Supabase (PostgreSQL + PostGIS)** | System of record — durable, relational, ACID | Incidents, emergency units, hospitals, users, audit logs, spatial data |
| **Redis (via Redisson)** | Coordination + speed layer — in-memory, shared across instances | Sessions, caches, distributed locks, rate limiters, IP blacklist, priority queue |

The rule of thumb: **durable domain data → Postgres; ephemeral state and
multi-instance coordination → Redis.** The backend itself is *stateless* — no
in-process HTTP session; every request is authenticated from its JWT plus a
Redis session lookup, so any instance can serve any request.

### Connection

Configured in `backend/src/main/resources/application.yml`:

```yaml
redisson:
  address: ${REDIS_ADDRESS:redis://127.0.0.1:6379}
  password: ${REDIS_PASSWORD:}
```

---

## 2. Redis key inventory

Defined in `util/RedisCacheKeys.java` and `config/CacheConfig.java`:

| Key / pattern | Redisson structure | Purpose | TTL |
| --- | --- | --- | --- |
| `session` (map) | `RMapCache` | Server session store (revocable JWT) | token lifetime |
| `incident:priority:queue` | `RScoredSortedSet` | Priority dispatch queue (bonus) | — |
| `lock:unit:{id}` | `RLock` | Mutex on unit assignment | 10s lease |
| `lock:bed:{id}` | `RLock` | Mutex on hospital bed reservation | 10s lease |
| `rl:login:{ip}` | `RRateLimiter` | Login limit: 5/min/IP | rolling 1 min |
| `rl:{userId}` | `RRateLimiter` | Per-user limit: 60/min | rolling 1 min |
| `blacklist:ips` | `RSet` | Blocked IP set (O(1) lookup) | — |
| `blacklist:count:{ip}` | `RAtomicLong` | Denial counter (auto-blacklist at 10/5min) | 5 min |
| `incidents:active` | Spring Cache | Active-incidents list | 300s |
| `incident` (`incident:{id}`) | Spring Cache | Incident detail | 180s |
| `resourcesAvail` / `resourceAvail` | Spring Cache | Hospital availability | 60s |
| `dashboard:summary` | Spring Cache | Dashboard metrics | 120s |

---

## 3. Redis-backed endpoints

> **Base URL:** `https://127.0.0.1:8443` (HTTP `:8080` redirects to HTTPS).
> Use a self-signed cert, so pass `-k` to `curl`. Use `127.0.0.1`, **not**
> `localhost`.
>
> **Auth:** all but `/health` require `Authorization: Bearer <JWT>`. Endpoints
> for `DISPATCHER` / `SYSTEM_ADMIN` roles additionally require an **MFA-completed
> (aal2 / `amr=totp`) token** — a plain password login is rejected with `403`.

### 3.1 Health — Redis connectivity ping
```
GET /api/v1/health        (no auth; localhost only)
```
Pings Redis via Redisson and checks Supabase. Returns `503` if either is down.
```bash
curl -k https://127.0.0.1:8443/api/v1/health
# {"status":"UP","redis":"UP","supabase":"UP"}
```

### 3.2 Sessions — Redis `session` map (revocable JWT)
```
POST   /api/v1/auth/session     # open server session (stored in Redis)
DELETE /api/v1/auth/session     # revoke session (logout); JWT rejected afterward
```
Statelessness with revocation: the JWT is validated cryptographically, then a
live Redis session is required on every request. Deleting it invalidates the
token immediately (before its natural expiry).

### 3.3 Caching — Redisson-backed Spring Cache
```
GET /api/v1/dashboard/summary          # cached 120s
GET /api/v1/incidents/{id}             # cached 180s
GET /api/v1/resources?emirate=...      # cached 60s
GET /api/v1/resources/{id}/availability# cached 60s
```
First call = cache miss (queries Supabase); subsequent calls = cache hit served
from Redis. Mutations evict the relevant keys.

```
PUT  /api/v1/resources/{id}/availability   # HOSPITAL_ADMIN/SYSTEM_ADMIN — evicts cache
POST /api/v1/admin/cache/purge             # SYSTEM_ADMIN — clears all Redis caches
```

### 3.4 Priority dispatch queue — Redisson `RScoredSortedSet` (bonus)
```
POST /api/v1/incidents                 # DISPATCHER — triage + ZADD to the queue
GET  /api/v1/dashboard/priority?topN=N # DISPATCHER/SYSTEM_ADMIN — live top-N (never cached)
```
Score = criticality weight × incident age (CRITICAL=100, HIGH=75, MEDIUM=50,
LOW=25). Higher-criticality incidents rank above newer lower-priority ones. On
status transition to `RESOLVED` the incident is `ZREM`-removed.

### 3.5 Distributed locks — Redisson `RLock`
```
PUT  /api/v1/resources/{id}/reserve    # DISPATCHER/SYSTEM_ADMIN — lock:bed:{id}
POST /api/v1/assignments               # DISPATCHER — lock:unit:{id}
DELETE /api/v1/assignments/{id}        # DISPATCHER — release the unit
```
Guarantees exactly-one-success on concurrent operations against the same
resource. The lock is acquired **outside** the transaction boundary and held
**across the commit**, so a waiting thread always observes committed state
(prevents double-assignment / lost updates). Contention that can't acquire the
lock returns `409 RESOURCE_LOCKED`.

### 3.6 Rate limiting — Redisson `RRateLimiter`
```
POST /api/v1/auth/session              # login limiter rl:login:{ip} — 5/min/IP -> 429
(all authenticated requests)           # per-user limiter rl:{userId} — 60/min -> 429
```
Exceeding the limit returns `429 TOO_MANY_REQUESTS` with `Retry-After: 60`.

### 3.7 IP blacklist — Redisson `RSet` + `RAtomicLong`
```
POST   /api/v1/admin/blacklist?ip=...  # SYSTEM_ADMIN — add IP (subsequent requests 403)
DELETE /api/v1/admin/blacklist?ip=...  # SYSTEM_ADMIN — remove IP
```
IPs producing 10+ denied (401/403) responses within a 5-minute window are
auto-blacklisted.

---

## 4. Auth requirements at a glance

| Endpoint | Role | TOTP (aal2)? |
| --- | --- | :--: |
| `GET /health` | none | ❌ |
| `POST`/`DELETE /auth/session` | any authenticated | ❌ |
| `GET /dashboard/summary`, `GET /incidents`, `GET /incidents/{id}` | any authenticated | ❌ |
| `GET /dashboard/priority` | DISPATCHER / SYSTEM_ADMIN | ✅ |
| `POST /incidents`, `POST`/`DELETE /assignments` | DISPATCHER | ✅ |
| `PUT /resources/{id}/reserve` | DISPATCHER / SYSTEM_ADMIN | ✅ |
| `POST /admin/cache/purge`, `*/admin/blacklist` | SYSTEM_ADMIN | ✅ |

---

## 5. Quick demo

```bash
TOKEN='<aal2 access_token>'    # browser login + TOTP, then copy from
                              # localStorage key sb-<ref>-auth-token -> access_token
BASE=https://127.0.0.1:8443/api/v1
AUTH="Authorization: Bearer $TOKEN"

# 1. health (Redis ping)
curl -k $BASE/health

# 2. open session (required before other calls)
curl -k -X POST $BASE/auth/session -H "$AUTH"

# 3. cache: call twice (miss then hit)
curl -k $BASE/dashboard/summary -H "$AUTH"

# 4. priority queue: create CRITICAL incident, then read top-N
curl -k -X POST $BASE/incidents -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"description":"Cardiac arrest, chest pain","latitude":24.45,"longitude":54.37}'
curl -k "$BASE/dashboard/priority?topN=5" -H "$AUTH"

# 5. distributed lock: two concurrent assignments of the same unit -> 1x201 + 1x409
#    (see artifacts/lock-demo.sh)

# 6. login rate limit: 6x rapid POST /auth/session -> 429 + Retry-After
for i in $(seq 1 6); do curl -k -s -o /dev/null -w "%{http_code}\n" -X POST $BASE/auth/session; done
```

---

## 6. Implementation map

| Concern | Class |
| --- | --- |
| Key constants | `util/RedisCacheKeys.java` |
| Cache manager / TTLs | `config/CacheConfig.java` |
| Redisson client | `config/RedissonConfig.java` |
| Sessions | `auth/AuthServiceImpl.java` |
| Priority queue | `incident/PriorityDispatchQueue.java`, `dashboard/DashboardService.java` |
| Unit lock | `assignment/AssignmentServiceImpl.java` |
| Bed lock + caching | `resource/ResourceServiceImpl.java` |
| Rate limiting | `security/RateLimitFilter.java`, `security/JwtValidationFilter.java` |
| IP blacklist | `security/IpBlacklist.java` |
| Health | `health/HealthController.java` |
