// Authentication state for the whole app.
//
// Flow (SRS 8.5):
//   1. Email + password via Supabase Auth.
//   2. Dispatcher / System Admin must reach assurance level aal2 via TOTP —
//      verify an existing authenticator, or enrol one by scanning a QR code.
//   3. Once the token is at the required assurance level, POST /api/v1/auth/session
//      opens the backend Redis session. Only then is the user "authed"; every
//      other backend call needs that session or returns 401.
//
// Design: we mirror Supabase's session into state via onAuthStateChange, and a
// single effect opens the backend session when the token becomes usable. The
// UI only treats the user as signed in once that backend session exists, which
// keeps the dashboard from rendering (and 401-ing) too early.
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { Session } from '@supabase/supabase-js';
import { supabase, roleFromMetadata, type AppRole } from '../lib/supabase';
import api from '../lib/api';

// Roles the backend requires TOTP (aal2) for on every non-session request.
const MFA_ROLES: AppRole[] = ['DISPATCHER', 'SYSTEM_ADMIN'];

export type MfaStep =
  | { mode: 'verify' }
  | { mode: 'enroll'; qrCode: string; secret: string; uri: string };

interface AuthState {
  session: Session | null;
  role: AppRole | null;
  loading: boolean;
  /** Token meets the assurance level the role requires (aal2 for MFA roles). */
  mfaSatisfied: boolean;
  /** Fully signed in: MFA satisfied AND the backend Redis session is open. */
  authed: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  prepareMfa: () => Promise<MfaStep>;
  verifyMfa: (code: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

// Read the `aal` claim straight out of the JWT (no extra network call).
const aalOf = (s: Session | null): string | null => {
  if (!s?.access_token) return null;
  try {
    const payload = s.access_token.split('.')[1];
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))).aal ?? null;
  } catch {
    return null;
  }
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [session, setSession] = useState<Session | null>(null);
  const [backendReady, setBackendReady] = useState(false);
  const [loading, setLoading] = useState(true);
  const pendingFactorId = useRef<string | null>(null);

  const role = useMemo(() => (session ? roleFromMetadata(session.user.app_metadata) : null), [session]);
  const mfaSatisfied = useMemo(() => {
    if (!session || !role) return false;
    return MFA_ROLES.includes(role) ? aalOf(session) === 'aal2' : true;
  }, [session, role]);
  const authed = !!session && mfaSatisfied && backendReady;

  // Mirror Supabase auth state into React (initial load + refresh + sign-out).
  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setLoading(false);
    });
    const { data: sub } = supabase.auth.onAuthStateChange((_event, s) => {
      setSession(s);
      if (!s) setBackendReady(false);
    });
    return () => sub.subscription.unsubscribe();
  }, []);

  // The single place that opens the backend session — runs once the token is
  // usable (non-MFA role, or MFA role at aal2) and the session isn't open yet.
  useEffect(() => {
    if (!session || !mfaSatisfied || backendReady) return;
    let cancelled = false;
    api.post('/auth/session')
      .then(() => { if (!cancelled) setBackendReady(true); })
      .catch(() => { /* surfaced via the api interceptor toast; stay un-authed */ });
    return () => { cancelled = true; };
  }, [session?.access_token, mfaSatisfied, backendReady]);

  const signIn = useCallback(async (email: string, password: string) => {
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) throw error;
    // onAuthStateChange updates `session`; the effect above opens the backend
    // session; the Login page drives the TOTP step if the role needs it.
  }, []);

  // Decide whether to verify an existing authenticator or enrol a new one, and
  // stash the factor id for verifyMfa().
  const prepareMfa = useCallback(async (): Promise<MfaStep> => {
    const { data: factors, error } = await supabase.auth.mfa.listFactors();
    if (error) throw error;

    const verified = factors.totp.find((f) => f.status === 'verified');
    if (verified) {
      pendingFactorId.current = verified.id;
      return { mode: 'verify' };
    }

    // Clear any half-finished factor so enrol() returns a fresh QR/secret.
    for (const f of factors.all.filter((f) => f.factor_type === 'totp' && f.status !== 'verified')) {
      await supabase.auth.mfa.unenroll({ factorId: f.id });
    }

    const { data: enrolled, error: enrollErr } = await supabase.auth.mfa.enroll({
      factorType: 'totp',
      friendlyName: 'EmergencyConnect Authenticator',
    });
    if (enrollErr) throw enrollErr;

    pendingFactorId.current = enrolled.id;
    return { mode: 'enroll', qrCode: enrolled.totp.qr_code, secret: enrolled.totp.secret, uri: enrolled.totp.uri };
  }, []);

  const verifyMfa = useCallback(async (code: string) => {
    const factorId = pendingFactorId.current;
    if (!factorId) throw new Error('No MFA challenge in progress.');

    const challenge = await supabase.auth.mfa.challenge({ factorId });
    if (challenge.error) throw challenge.error;

    const verify = await supabase.auth.mfa.verify({ factorId, challengeId: challenge.data.id, code });
    if (verify.error) throw verify.error;

    pendingFactorId.current = null;
    // Pull the upgraded (aal2) session; the effect above then opens the backend
    // session and `authed` flips true.
    const { data } = await supabase.auth.getSession();
    setSession(data.session);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await api.delete('/auth/session');
    } catch {
      /* best effort — log out locally even if the server call fails */
    }
    await supabase.auth.signOut();
    pendingFactorId.current = null;
    setSession(null);
    setBackendReady(false);
  }, []);

  const value = useMemo<AuthState>(
    () => ({ session, role, loading, mfaSatisfied, authed, signIn, prepareMfa, verifyMfa, signOut }),
    [session, role, loading, mfaSatisfied, authed, signIn, prepareMfa, verifyMfa, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthState => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
};
