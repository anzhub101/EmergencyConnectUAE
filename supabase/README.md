# Supabase migrations

Files (run in this order):

1. `migrations/01_extensions_and_schema.sql` — PostGIS + the six tables
2. `migrations/02_indexes.sql` — GIST spatial + lookup indexes
3. `migrations/03_auth_sync_trigger.sql` — syncs `auth.users` → `public.users` (maps `app_metadata.role`)
4. `migrations/04_seed.sql` — hospitals, units, sample incidents

`migrations/00_apply_all.sql` is a generated concatenation of all four for one-paste application.

## How to apply (pick one)

**A. Supabase SQL Editor (easiest, no tooling).**
Dashboard → SQL Editor → New query → paste the contents of
`migrations/00_apply_all.sql` → Run. If the project is paused, open it first so it
resumes. Safe to re-run (the schema migration drops + recreates the app tables).

**B. Node script (from a machine that can reach the DB).**
```bash
npm install pg
ECU_DB_URL="postgresql://postgres:<password>@db.jdevzqbzbxnhqnynrmgg.supabase.co:5432/postgres" \
  node supabase/apply-migrations.mjs
```
On IPv4-only networks use the session pooler instead:
`postgresql://postgres.jdevzqbzbxnhqnynrmgg:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres`

**C. Supabase MCP (so Claude can apply it).**
Authenticate the server in a regular terminal — `claude /mcp` → select `supabase`
→ Authenticate — then ask Claude to apply the migrations.

## After applying — create demo Auth users

In Dashboard → Authentication → Users, create one account per role and set
`app_metadata.role` (the trigger maps it to the app `users` table):

| email | app_metadata.role | MFA |
| --- | --- | --- |
| dispatcher@demo.ae | `dispatcher` | enroll TOTP |
| responder@demo.ae | `responder` | — |
| hospital@demo.ae | `hospital_admin` | — |
| sysadmin@demo.ae | `system_admin` | enroll TOTP |
| viewer@demo.ae | `read_only` | — |

Set `app_metadata` with the Admin API or SQL, e.g.:
```sql
update auth.users
set raw_app_meta_data = raw_app_meta_data || '{"role":"dispatcher"}'
where email = 'dispatcher@demo.ae';
```
