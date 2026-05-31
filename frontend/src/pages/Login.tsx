import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { toast } from '../lib/toast';
import { Loader2, ShieldCheck, QrCode } from 'lucide-react';

type Stage = 'credentials' | 'verify' | 'enroll';

export const Login = () => {
  const { session, mfaSatisfied, signIn, prepareMfa, verifyMfa } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [stage, setStage] = useState<Stage>('credentials');
  const [enroll, setEnroll] = useState<{ qrCode: string; secret: string } | null>(null);
  const [busy, setBusy] = useState(false);
  // Guards prepareMfa() from running twice (React 18 StrictMode / re-renders).
  const preparing = useRef(false);

  // Once signed in, either proceed or branch into the TOTP step.
  useEffect(() => {
    if (!session) return;
    if (mfaSatisfied) {
      navigate(from, { replace: true });
      return;
    }
    if (preparing.current) return;
    preparing.current = true;
    (async () => {
      try {
        const step = await prepareMfa();
        if (step.mode === 'enroll') {
          setEnroll({ qrCode: step.qrCode, secret: step.secret });
          setStage('enroll');
        } else {
          setStage('verify');
        }
      } catch (err) {
        toast(err instanceof Error ? err.message : 'Could not start two-factor setup.', 'error');
        preparing.current = false;
      }
    })();
  }, [session, mfaSatisfied, prepareMfa, navigate, from]);

  const submitCredentials = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await signIn(email.trim(), password);
      // Navigation / MFA branching is handled by the effect above.
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Sign-in failed.', 'error');
    } finally {
      setBusy(false);
    }
  };

  const submitCode = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await verifyMfa(code.trim());
      // mfaSatisfied flips true -> effect navigates.
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Invalid code — try again.', 'error');
    } finally {
      setBusy(false);
    }
  };

  const inputClass =
    'w-full px-3 py-2.5 rounded-lg bg-background border border-border text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/60';

  return (
    <div className="flex min-h-screen items-center justify-center p-6 bg-background">
      <div className="glass-card max-w-md w-full p-8 space-y-6 animate-slide-in">
        <div className="text-center space-y-2">
          <h1 className="text-3xl font-bold tracking-tighter text-foreground">EmergencyConnectUAE™</h1>
          <p className="text-muted-foreground text-sm">
            A distributed emergency coordination platform for public safety in the UAE.
          </p>
        </div>

        {stage === 'credentials' && (
          <form onSubmit={submitCredentials} className="space-y-4">
            <div className="space-y-1.5">
              <label htmlFor="email" className="text-sm font-medium text-foreground">Email</label>
              <input
                id="email"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClass}
                placeholder="you@example.com"
              />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="password" className="text-sm font-medium text-foreground">Password</label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClass}
                placeholder="••••••••"
              />
            </div>
            <button
              type="submit"
              disabled={busy}
              className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg bg-primary text-primary-foreground font-semibold hover:bg-primary/90 transition-all active:scale-95 disabled:opacity-60 disabled:active:scale-100"
            >
              {busy && <Loader2 size={16} className="animate-spin" />}
              Sign In
            </button>
          </form>
        )}

        {stage === 'enroll' && enroll && (
          <form onSubmit={submitCode} className="space-y-4">
            <div className="flex items-center gap-2 text-sm text-foreground font-medium">
              <QrCode size={16} className="text-primary" />
              Set up two-factor authentication
            </div>
            <p className="text-xs text-muted-foreground">
              Scan this code with an authenticator app (Google Authenticator, Authy, Microsoft
              Authenticator, 1Password…), then enter the 6-digit code it shows.
            </p>
            <div className="flex justify-center">
              <img
                src={enroll.qrCode}
                alt="Authenticator QR code"
                className="w-44 h-44 rounded-lg bg-white p-2"
              />
            </div>
            <div className="text-xs text-muted-foreground text-center">
              Can’t scan? Enter this key manually:
              <code className="block mt-1 break-all text-foreground font-mono">{enroll.secret}</code>
            </div>
            <CodeInput code={code} setCode={setCode} inputClass={inputClass} />
            <SubmitCode busy={busy} code={code} label="Verify & Enable" />
          </form>
        )}

        {stage === 'verify' && (
          <form onSubmit={submitCode} className="space-y-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <ShieldCheck size={16} className="text-primary" />
              Two-factor authentication required.
            </div>
            <p className="text-xs text-muted-foreground">
              Enter the 6-digit code from your authenticator app.
            </p>
            <CodeInput code={code} setCode={setCode} inputClass={inputClass} />
            <SubmitCode busy={busy} code={code} label="Verify" />
          </form>
        )}
      </div>
    </div>
  );
};

const CodeInput = ({
  code,
  setCode,
  inputClass,
}: {
  code: string;
  setCode: (v: string) => void;
  inputClass: string;
}) => (
  <div className="space-y-1.5">
    <label htmlFor="code" className="text-sm font-medium text-foreground">Authenticator code</label>
    <input
      id="code"
      inputMode="numeric"
      autoComplete="one-time-code"
      pattern="[0-9]*"
      maxLength={6}
      required
      autoFocus
      value={code}
      onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
      className={`${inputClass} tracking-[0.5em] text-center text-lg`}
      placeholder="000000"
    />
  </div>
);

const SubmitCode = ({ busy, code, label }: { busy: boolean; code: string; label: string }) => (
  <button
    type="submit"
    disabled={busy || code.length < 6}
    className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg bg-primary text-primary-foreground font-semibold hover:bg-primary/90 transition-all active:scale-95 disabled:opacity-60 disabled:active:scale-100"
  >
    {busy && <Loader2 size={16} className="animate-spin" />}
    {label}
  </button>
);
