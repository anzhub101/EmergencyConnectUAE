import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { onToast, type ToastDetail } from '../lib/toast';

const ICONS = {
  error: <AlertTriangle size={16} className="text-destructive" />,
  success: <CheckCircle2 size={16} className="text-green-500" />,
  info: <Info size={16} className="text-primary" />,
};

export const Toaster = () => {
  const [toasts, setToasts] = useState<ToastDetail[]>([]);

  useEffect(() => onToast((t) => {
    setToasts((cur) => [...cur, t]);
    setTimeout(() => setToasts((cur) => cur.filter((x) => x.id !== t.id)), 5000);
  }), []);

  const dismiss = (id: number) => setToasts((cur) => cur.filter((x) => x.id !== id));

  return (
    <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 w-80 max-w-[calc(100vw-2rem)]">
      {toasts.map((t) => (
        <div key={t.id}
          className="glass-card flex items-start gap-2 p-3 shadow-lg border border-border animate-in">
          <span className="mt-0.5">{ICONS[t.type]}</span>
          <span className="flex-1 text-sm text-foreground">{t.message}</span>
          <button onClick={() => dismiss(t.id)} className="text-muted-foreground hover:text-foreground">
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
};
