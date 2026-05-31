// Browser Supabase client. Uses the publishable (anon) key — safe to expose.
// Auth tokens are persisted to localStorage and auto-refreshed by supabase-js.
import { createClient } from '@supabase/supabase-js';

const url = import.meta.env.VITE_SUPABASE_URL;
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

if (!url || !anonKey) {
  // Fail loudly during development if the env is misconfigured, rather than
  // letting sign-in silently do nothing.
  throw new Error(
    'Missing VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY. Copy frontend/.env.example to frontend/.env and fill them in.',
  );
}

export const supabase = createClient(url, anonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: false,
  },
});

/**
 * App role as used by the UI (e.g. 'DISPATCHER'). Derived from the Supabase
 * user's `app_metadata.role`, which the auth-sync trigger also maps into
 * `public.users.role` (where the backend reads it). We normalise to the bare,
 * upper-cased form so it matches the comparisons in the page components.
 */
export type AppRole =
  | 'DISPATCHER'
  | 'RESPONDER'
  | 'HOSPITAL_ADMIN'
  | 'SYSTEM_ADMIN'
  | 'READ_ONLY';

export const roleFromMetadata = (appMetadata: Record<string, unknown> | undefined): AppRole => {
  const raw = String(appMetadata?.role ?? 'read_only');
  const normalised = raw.toUpperCase().replace(/^ROLE_/, '');
  const allowed: AppRole[] = ['DISPATCHER', 'RESPONDER', 'HOSPITAL_ADMIN', 'SYSTEM_ADMIN', 'READ_ONLY'];
  return (allowed as string[]).includes(normalised) ? (normalised as AppRole) : 'READ_ONLY';
};
