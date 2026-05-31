// Applies all SQL migrations in order to a Postgres/Supabase database.
//
// Usage (from your own machine, which can reach the Supabase host):
//   npm install pg
//   ECU_DB_URL="postgresql://postgres:<password>@db.<ref>.supabase.co:5432/postgres" \
//     node supabase/apply-migrations.mjs
//
// Or with the IPv4 session pooler:
//   ECU_DB_URL="postgresql://postgres.<ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres" \
//     node supabase/apply-migrations.mjs
//
// Re-running is safe: the schema migration drops+recreates the app tables.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const here = dirname(fileURLToPath(import.meta.url));
const FILES = [
  '01_extensions_and_schema.sql',
  '02_indexes.sql',
  '03_auth_sync_trigger.sql',
  '04_seed.sql',
];

const url = process.env.ECU_DB_URL;
if (!url) {
  console.error('Set ECU_DB_URL to your Postgres connection string.');
  process.exit(1);
}

const client = new pg.Client({ connectionString: url, ssl: { rejectUnauthorized: false } });

try {
  await client.connect();
  for (const f of FILES) {
    const sql = readFileSync(join(here, 'migrations', f), 'utf8');
    process.stdout.write(`Applying ${f} ... `);
    await client.query(sql);
    console.log('ok');
  }
  console.log('\nAll migrations applied successfully.');
} catch (e) {
  console.error('\nMigration failed:', e.message);
  process.exitCode = 1;
} finally {
  await client.end();
}
