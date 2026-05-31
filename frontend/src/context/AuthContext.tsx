// Authentication state for the whole app.
//
// Flow (SRS 8.5):
//   1. Email + password sign-in via Supabase Auth.
//   2. Dispatcher / System Admin must reach assurance level aal2 via TOTP:
//        - if a verified authenticator factor exists  -> challenge + verify a code
//        - if none exists yet                         -> self-service enrolment
//          (scan a QR code into an authenticator app, then verify a code)
//      Verifying upgrades the access token so it carries `amr: totp` / aal2.
//   3. Once the token is at the required assurance level we POST it to
//      /api/v1/auth/session so the backend opens the matching Redis session.
//      Without that session every other backend call returns 401.
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
  /** True once the user has met the assurance level their role requires. */
  mfaSatisfied: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  /** Decide whether the user must verify an existing factor or enrol a new one. */
  prepareMfa: () => Promise<MfaStep>;
  verifyMfa: (code: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

// Read the `aal` claim straight out of the JWT (no extra network call).
const aalFromSession = (s: Session | null): string | null => {
  if (!s?.access_token) return null;
  try {
    const payload = s.access_token.split('.')[1];
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return typeof json.aal === 'string' ? json.aal : null;
  } catch {
    return null;
  }
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [session, setSession] = useState<Session | null>(null);
  const [role, setRole] = useState<AppRole | null>(null);
  const [aal, setAal] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // Factor id captured while we wait for the user to enter a TOTP code.
  const pendingFactorId = useRef<string | null>(null);
  // Avoid opening the backend session twice for the same access token.
  const sessionedToken = useRef<string | null>(null);

  const mfaSatisfied = useMemo(() => {
    if (!session || !role) return false;
    return MFA_ROLES.includes(role) ? aal === 'aal2' : true;
  }, [session, role, aal]);

  const applySession = useCallback((s: Session | null) => {
    setSession(s);
    setRole(s ? roleFromMetadata(s.user.app_metadata) : null);
    setAal(aalFromSession(s));
  }, []);

  // Open (or refresh) the backend Redis session for the current access token.
  const ensureBackendSession = useCallback(async (s: Session | null) => {
    if (!s) return;
    if (sessionedToken.current === s.access_token) return;
    await api.post('/auth/session');
    sessionedToken.current = s.access_token;
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const { data, error } = await supabase.auth.signInWithPassword({ email, password });
      if (error) throw error;
      applySession(data.session);
      // Roles without an MFA requirement are ready immediately.
      const r = data.session ? roleFromMetadata(data.session.user.app_metadata) : null;
      if (r && !MFA_ROLES.includes(r)) {
        await ensureBackendSession(data.session);
      }
    },
    [applySession, ensureBackendSession],
  );

  // Called when the role needs aal2 but the session isn't there yet. Returns
  // whether to verify an existing factor or run first-time enrolment, and
  // stashes the factor id for verifyMfa().
  const prepareMfa = useCallback(async (): Promise<MfaStep> => {
    const { data: factors, error } = await supabase.auth.mfa.listFactors();
    if (error) throw error;

    const verified = factors.totp.find((f) => f.status === 'verified');
    if (verified) {
      pendingFactorId.current = verified.id;
      return { mode: 'verify' };
    }

    // Clear any half-finished factor so enrol() returns a fresh QR/secret.
    const stale = factors.all.filter((f) => f.factor_type === 'totp' && f.status !== 'verified');
    for (const f of stale) {
      await supabase.auth.mfa.unenroll({ factorId: f.id });
    }

    const { data: enrolled, error: enrollErr } = await supabase.auth.mfa.enroll({
      factorType: 'totp',
      friendlyName: 'EmergencyConnect Authenticator',
    });
    if (enrollErr) throw enrollErr;

    pendingFactorId.current = enrolled.id;
    return {
      mode: 'enroll',
      qrCode: enrolled.totp.qr_code,
      secret: enrolled.totp.secret,
      uri: enrolled.totp.uri,
    };
  }, []);

  const verifyMfa = useCallback(
    async (code: string) => {
      const factorId = pendingFactorId.current;
      if (!factorId) throw new Error('No MFA challenge in progress.');

      const challenge = await supabase.auth.mfa.challenge({ factorId });
      if (challenge.error) throw challenge.error;

      const verify = await supabase.auth.mfa.verify({
        factorId,
        challengeId: challenge.data.id,
        code,
      });
      if (verify.error) throw verify.error;

      pendingFactorId.current = null;
      // verify() returns the upgraded (aal2) session; use it directly.
      applySession(verify.data);
      await ensureBackendSession(verify.data);
    },
    [applySession, ensureBackendSession],
  );

  const signOut = useCallback(async () => {
    try {
      await api.delete('/auth/session');
    } catch {
      // Best effort — log out locally even if the server call fails.
    }
    await supabase.auth.signOut();
    sessionedToken.current = null;
    pendingFactorId.current = null;
    applySession(null);
  }, [applySession]);

  // Restore an existing session on load and react to token refreshes.
  useEffect(() => {
    let active = true;

    (async () => {
      const { data } = await supabase.auth.getSession();
      if (!active) return;
      applySession(data.session);
      if (data.session) {
        const r = roleFromMetadata(data.session.user.app_metadata);
        const ready = !MFA_ROLES.includes(r) || aalFromSession(data.session) === 'aal2';
        if (ready) {
          try {
            await ensureBackendSession(data.session);
          } catch {
            /* surfaced via the api interceptor toast */
          }
        }
      }
      setLoading(false);
    })();

    const { data: sub } = supabase.auth.onAuthStateChange((_event, s) => {
      applySession(s);
      if (!s) sessionedToken.current = null;
    });

    return () => {
      active = false;
      sub.subscription.unsubscribe();
    };
  }, [applySession, ensureBackendSession]);

  const value = useMemo<AuthState>(
    () => ({ session, role, loading, mfaSatisfied, signIn, prepareMfa, verifyMfa, signOut }),
    [session, role, loading, mfaSatisfied, signIn, prepareMfa, verifyMfa, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthState => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
};
