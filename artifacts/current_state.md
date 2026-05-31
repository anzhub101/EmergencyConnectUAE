# EmergencyConnectUAE — Current State

_Last updated: 2026-05-31. Branch: `main`. Source of truth for requirements: `artifacts/EmergencyConnectUAE_SRS_v4_final.md`._

This note captures the working state after wiring up the frontend authentication
layer and fixing the Redis session bugs. It is meant as a quick orientation +
test guide, not a replacement for the SRS or `walkthrough.md`.

---

## 1. Summary

- **Backend** (Spring Boot, JDK 21): builds and runs. Implements the full SRS
  API surface (auth/session, incidents, assignments, resources, triage,
  dashboard, audit, admin) behind a Supabase-JWT resource server with Redis
  sessions, rate limiting, IP blacklist, and RBAC.
- **Frontend** (React + Vite + TypeScript): the authentication layer, routing,
  and core pages are now in place. Sign-in works end to end, including TOTP MFA.
- **Database** (Supabase Postgres + PostGIS): migrations applied; seed data
  present (5 hospitals, 10 emergency units, 4 incidents, 5 users).

---

## 2. What was built this session (frontend)

Previously `frontend/src/App.tsx` was a placeholder: a "Sign In" button with no
handler, no router, and no auth code. The pages that existed (`Admin`,
`IncidentDetail`) imported modules that did not exist on disk. The following
were created/wired so the app actually functions:

| File | Purpose |
| --- | --- |
| `frontend/src/lib/supabase.ts` | Browser Supabase client + role normalisation from `app_metadata.role`. |
| `frontend/src/lib/api.ts` | Axios instance (`/api/v1`) that injects the Supabase bearer token and toasts on 401/403/429/5xx. |
| `frontend/src/context/AuthContext.tsx` | Auth state: password sign-in, **self-service TOTP enrolment + verification**, backend session creation, sign-out, session restore. |
| `frontend/src/components/Sidebar.tsx` | App navigation + sign-out + role display. |
| `frontend/src/pages/Login.tsx` | Email/password → QR enrolment (first time) or 6-digit code (returning) for MFA roles. |
| `frontend/src/pages/Dashboard.tsx` | Stat cards, incident list, and the dispatcher "New Incident" modal (AI triage + create). |
| `frontend/src/App.tsx` | `BrowserRouter` + `AuthProvider` + protected routes + `Toaster`. |

### Authentication flow (matches SRS 8.5)

1. Email + password via Supabase Auth.
2. **Dispatcher / System Admin** must reach assurance level `aal2` via TOTP:
   - no factor yet → scan a QR code into any authenticator app (Google
     Authenticator, Authy, Microsoft Authenticator, 1Password, …), then verify a
     code (standard RFC 6238 TOTP — no SMS/email).
   - factor already verified → enter the 6-digit code.
3. Once at the required assurance level, the app calls `POST /api/v1/auth/session`
   so the backend opens the matching Redis session. Without that session every
   other backend call returns 401.

`RequireAuth` gates protected routes on both a live session **and** the MFA
assurance level required by the role.

---

## 3. Bugs fixed

### 3.1 Redis session keyed by a non-existent claim (fixed previously)

**Symptom:** after signing in (including TOTP), the dashboard showed repeated
"Session expired or revoked — please sign in again" toasts and no data loaded.

**Root cause:** Supabase access tokens contain `session_id` and `sub` but **no
`jti` claim**. The backend keyed the Redis session by `jwt.getId()` (the `jti`),
so `POST /auth/session` stored the session under a null key.

**Fix:** Backend now keys the session by the stable `session_id` claim (falling
back to the subject).

### 3.2 Frontend race condition in `onAuthStateChange` (fixed this session)

**Symptom:** same as 3.1 — "Session expired or revoked" toasts immediately after
successful MFA verification, dashboard shows 0 for all stats.

**Root cause:** When `supabase.auth.mfa.verify()` succeeds, Supabase fires an
`onAuthStateChange` event. The listener called `applySession(s)` synchronously,
which set `mfaSatisfied = true`, causing React to render the Dashboard. The
Dashboard immediately fired `GET /dashboard/summary` and `GET /incidents`, but
`ensureBackendSession()` (which POSTs to `/auth/session` to create the Redis
entry) had not completed yet → 401.

On page refresh, the same race existed: the `useEffect` called `applySession`
*before* `ensureBackendSession`.

**Fix in `AuthContext.tsx`:**

1. **`onAuthStateChange` handler** now calls `ensureBackendSession(s)` before
   `applySession(s)` for sessions that are ready (non-MFA roles, or MFA roles
   with aal2). The guard inside `ensureBackendSession`
   (`sessionedToken.current === s.access_token`) prevents double-POSTing.

2. **Session restore on mount** now calls `ensureBackendSession` before
   `applySession`, so on page refresh the Redis session exists before the
   Dashboard renders.

### 3.3 DB password angle brackets (fixed this session)

**Symptom:** backend logs show `PSQLException: The connection attempt failed`
with `SocketTimeoutException: Connect timed out`.

**Root cause:** `DB_PASSWORD=<EmergencyConnectUAE>` in `.env`. The `<>` are
interpreted as shell redirections by `export $(grep -v '^#' .env | xargs)`,
resulting in an empty or garbled password. Additionally, the `application.yml`
default had the same angle brackets.

**Fix:** Removed angle brackets from both `.env` and `application.yml` defaults.

> Note: the direct DB host (`db.jdevzqbzbxnhqnynrmgg.supabase.co:5432`) is
> IPv6-only. From IPv4-only networks it times out on initial startup, but
> HikariCP retries lazily and eventually connects. The Supabase connection
> pooler does not recognise this project ref, so we use the direct host.

---

## 4. Demo accounts

All five accounts share the password **`Demo1234!`** (set via SQL on the hosted
Supabase project; emails are confirmed).

| Email | Role | MFA | Fully usable now |
| --- | --- | --- | --- |
| viewer@demo.ae | read_only | — | ✅ |
| responder@demo.ae | responder | — | ✅ |
| hospital@demo.ae | hospital_admin | — | ✅ |
| dispatcher@demo.ae | dispatcher | TOTP **verified** | ✅ (enter authenticator code) |
| sysadmin@demo.ae | system_admin | not enrolled | ✅ after on-screen QR enrolment |

---

## 5. Seed data present

| Table | Rows |
| --- | --- |
| hospitals | 5 |
| emergency_units | 10 |
| incidents | 4 |
| users | 5 |
| assignments | 0 |

Enough to demonstrate every flow. Optionally expandable for a richer demo
(incidents across all emirates, pre-existing assignments).

---

## 6. How to run

1. **Redis** running locally (`localhost:6379`).
2. **Backend:** `cd backend && ./mvnw spring-boot:run` (JDK 21). Set `.env` /
   environment variables per `application.yml` (DB password, JWKS URI, etc.).
3. **Frontend:** `cd frontend && npm run dev` → http://localhost:3000
   (Vite proxies `/api` → `localhost:8080`). Requires `frontend/.env` with
   `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`.

---

## 7. How to test each feature through the UI

- **Sign-in + MFA:** dispatcher/sysadmin show the TOTP step; the three other
  roles go straight in.
- **AI Triage:** Dashboard → **New Incident** → enter a description →
  **Analyze with AI Triage Engine** (`POST /triage/analyze`). Returns
  criticality, confidence, recommended units/hospital tier, matched keywords.
- **Create + dispatch incident:** same modal → **File & Dispatch**
  (`POST /incidents`).
- **PostGIS:** open any incident (Dashboard → click a row → `IncidentDetail`).
  It calls `GET /resources/proximity?lat=…&lng=…&radius=…`, which runs the
  PostGIS spatial query (distance + ETA of nearby units). Assigning a unit there
  exercises `POST /assignments`.
- **Incident status transitions:** IncidentDetail → IN_PROGRESS / RESOLVED.
- **RBAC / audit / admin:** `Admin` page — audit logs, hospital bed availability
  (hospital_admin), IP blacklist + cache purge (system_admin). Sign in as
  different roles to observe RBAC denials (also written to `audit_logs`).

Direct PostGIS sanity check (SQL):

```sql
-- nearest units to Abu Dhabi City (lng, lat order for ST_MakePoint)
select id, type, status,
       round(ST_Distance(location, ST_SetSRID(ST_MakePoint(54.3773, 24.4539),4326)::geography)) as metres
from emergency_units
order by location <-> ST_SetSRID(ST_MakePoint(54.3773, 24.4539),4326)::geography
limit 5;
```

---

## 8. Known caveats

- **Rate limit:** 60 requests/min per user (SRS 8.4). The React dev server in
  StrictMode double-fires effects, so rapid clicking can occasionally return 429
  — harmless, just retry.
- **Token refresh:** sessions are keyed by `session_id`, which survives Supabase
  token refresh, so long-lived sessions no longer drop to 401 on refresh.
- **TOTP availability:** enrolment requires TOTP to be enabled in the Supabase
  project's Auth → MFA settings (on by default).
- **DB connectivity:** the direct DB host is IPv6-only. On IPv4-only networks
  the initial HikariCP connection may time out, but lazy reconnect succeeds.

---

## 9. Changes made this session

| File | Change |
| --- | --- |
| `frontend/src/context/AuthContext.tsx` | Fixed race condition: `onAuthStateChange` and mount-time session restore now call `ensureBackendSession` *before* `applySession`, preventing the Dashboard from firing API calls before the Redis session exists. |
| `backend/.env` | Removed `<>` brackets from `DB_PASSWORD`. Added `DB_USERNAME`. |
| `backend/src/main/resources/application.yml` | Removed `<>` from password default. Made username configurable via `DB_USERNAME` env var. |
