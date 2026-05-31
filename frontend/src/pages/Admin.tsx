import { useEffect, useMemo, useState } from 'react';
import { Sidebar } from '../components/Sidebar';
import api from '../lib/api';
import { useAuth } from '../context/AuthContext';
import { ScrollText, BedDouble, Save, ShieldBan, Ban, Trash2, Eraser } from 'lucide-react';
import { toast } from '../lib/toast';
import type { AuditLog, Paged, ResourceAvailability } from '../types';

type Tab = 'audit' | 'hospitals' | 'system';

export const Admin = () => {
  const { role } = useAuth();
  const canAudit = role === 'DISPATCHER' || role === 'SYSTEM_ADMIN';
  const canHospitals = role === 'HOSPITAL_ADMIN' || role === 'SYSTEM_ADMIN';
  const canSystem = role === 'SYSTEM_ADMIN';

  const tabs = useMemo(() => {
    const t: Tab[] = [];
    if (canAudit) t.push('audit');
    if (canHospitals) t.push('hospitals');
    if (canSystem) t.push('system');
    return t;
  }, [canAudit, canHospitals, canSystem]);

  const [tab, setTab] = useState<Tab>(tabs[0] ?? 'audit');
  useEffect(() => { if (tabs.length && !tabs.includes(tab)) setTab(tabs[0]); }, [tabs, tab]);

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <main className="flex-1 overflow-auto p-8">
        <h1 className="text-3xl font-bold tracking-tight text-foreground mb-6">Administration</h1>

        <div className="flex gap-2 mb-6 border-b border-border">
          {canAudit && (
            <TabButton active={tab === 'audit'} onClick={() => setTab('audit')} icon={<ScrollText size={16} />} label="Audit Logs" />
          )}
          {canHospitals && (
            <TabButton active={tab === 'hospitals'} onClick={() => setTab('hospitals')} icon={<BedDouble size={16} />} label="Hospital Availability" />
          )}
          {canSystem && (
            <TabButton active={tab === 'system'} onClick={() => setTab('system')} icon={<ShieldBan size={16} />} label="System Controls" />
          )}
        </div>

        {tabs.length === 0 && <p className="text-muted-foreground">You do not have access to any admin tools.</p>}
        {tab === 'audit' && canAudit && <AuditTab />}
        {tab === 'hospitals' && canHospitals && <HospitalsTab />}
        {tab === 'system' && canSystem && <SystemTab />}
      </main>
    </div>
  );
};

const TabButton = ({ active, onClick, icon, label }: { active: boolean; onClick: () => void; icon: React.ReactNode; label: string }) => (
  <button onClick={onClick}
    className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
      active ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground'}`}>
    {icon} {label}
  </button>
);

const AuditTab = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);

  useEffect(() => {
    api.get<Paged<AuditLog>>(`/audit/logs?page=${page}&size=20`)
      .then(res => { setLogs(res.data.data); setLast(res.data.last); })
      .catch(() => {});
  }, [page]);

  return (
    <div className="glass-card p-0 overflow-hidden">
      <table className="w-full text-sm text-left">
        <thead className="text-xs text-muted-foreground uppercase bg-muted/20 border-b border-border">
          <tr>
            <th className="px-4 py-3">Time</th>
            <th className="px-4 py-3">User</th>
            <th className="px-4 py-3">Action</th>
            <th className="px-4 py-3">Resource</th>
            <th className="px-4 py-3">Result</th>
          </tr>
        </thead>
        <tbody>
          {logs.map(l => (
            <tr key={l.id} className="border-b border-border/50">
              <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">{new Date(l.timestamp).toLocaleString()}</td>
              <td className="px-4 py-3 text-foreground">{l.userEmail}</td>
              <td className="px-4 py-3 text-foreground font-mono text-xs">{l.action}</td>
              <td className="px-4 py-3 text-muted-foreground">{l.resourceType}</td>
              <td className="px-4 py-3">
                <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                  l.result === 'SUCCESS' ? 'bg-green-500/15 text-green-500' : 'bg-destructive/15 text-destructive'}`}>{l.result}</span>
              </td>
            </tr>
          ))}
          {logs.length === 0 && <tr><td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">No audit entries.</td></tr>}
        </tbody>
      </table>
      <div className="flex justify-between items-center p-3 border-t border-border">
        <button disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}
          className="px-3 py-1.5 rounded-lg bg-muted/40 text-sm disabled:opacity-40">Previous</button>
        <span className="text-xs text-muted-foreground">Page {page + 1}</span>
        <button disabled={last} onClick={() => setPage(p => p + 1)}
          className="px-3 py-1.5 rounded-lg bg-muted/40 text-sm disabled:opacity-40">Next</button>
      </div>
    </div>
  );
};

const HospitalsTab = () => {
  const [rows, setRows] = useState<ResourceAvailability[]>([]);
  const [savingId, setSavingId] = useState<string | null>(null);

  const load = () => api.get<Paged<ResourceAvailability>>('/resources?size=50')
    .then(res => setRows(res.data.data)).catch(() => {});
  useEffect(() => { load(); }, []);

  const edit = (id: string, field: keyof ResourceAvailability, value: number) =>
    setRows(rs => rs.map(r => r.id === id ? { ...r, [field]: value } : r));

  const save = async (r: ResourceAvailability) => {
    setSavingId(r.id);
    try {
      await api.put(`/resources/${r.id}/availability`, {
        totalBeds: r.totalBeds, availableBeds: r.availableBeds, icuAvailable: r.icuAvailable,
      });
      await load();
    } catch (e) { console.error(e); }
    setSavingId(null);
  };

  return (
    <div className="glass-card p-0 overflow-hidden">
      <table className="w-full text-sm text-left">
        <thead className="text-xs text-muted-foreground uppercase bg-muted/20 border-b border-border">
          <tr>
            <th className="px-4 py-3">Hospital</th>
            <th className="px-4 py-3">Emirate</th>
            <th className="px-4 py-3">Total beds</th>
            <th className="px-4 py-3">Available</th>
            <th className="px-4 py-3">ICU</th>
            <th className="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.id} className="border-b border-border/50">
              <td className="px-4 py-3 text-foreground">{r.name}</td>
              <td className="px-4 py-3 text-muted-foreground">{r.emirate}</td>
              <td className="px-4 py-3"><NumInput value={r.totalBeds} onChange={v => edit(r.id, 'totalBeds', v)} /></td>
              <td className="px-4 py-3"><NumInput value={r.availableBeds} onChange={v => edit(r.id, 'availableBeds', v)} /></td>
              <td className="px-4 py-3"><NumInput value={r.icuAvailable} onChange={v => edit(r.id, 'icuAvailable', v)} /></td>
              <td className="px-4 py-3">
                <button disabled={savingId === r.id} onClick={() => save(r)}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-primary text-primary-foreground text-xs font-medium hover:bg-primary/90 disabled:opacity-40">
                  <Save size={14} /> {savingId === r.id ? 'Saving' : 'Save'}
                </button>
              </td>
            </tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">No hospitals.</td></tr>}
        </tbody>
      </table>
    </div>
  );
};

const NumInput = ({ value, onChange }: { value: number; onChange: (v: number) => void }) => (
  <input type="number" min={0} value={value} onChange={(e) => onChange(parseInt(e.target.value || '0', 10))}
    className="w-20 bg-background border border-border rounded px-2 py-1 outline-none focus:ring-2 focus:ring-primary text-foreground" />
);

// System Admin only: Redis IP blacklist management and cache purge (SRS Section 4).
const SystemTab = () => {
  const [ip, setIp] = useState('');
  const [busy, setBusy] = useState<string | null>(null);

  const valid = /^(\d{1,3}\.){3}\d{1,3}$|^[0-9a-fA-F:]+$/.test(ip.trim());

  const blacklist = async (action: 'add' | 'remove') => {
    const target = ip.trim();
    if (!valid) { toast('Enter a valid IPv4/IPv6 address.', 'error'); return; }
    setBusy(action);
    try {
      if (action === 'add') {
        await api.post(`/admin/blacklist?ip=${encodeURIComponent(target)}`);
        toast(`IP ${target} added to the blacklist.`, 'success');
      } else {
        await api.delete(`/admin/blacklist?ip=${encodeURIComponent(target)}`);
        toast(`IP ${target} removed from the blacklist.`, 'success');
      }
      setIp('');
    } catch { /* 403/429 already surfaced by the api interceptor */ }
    setBusy(null);
  };

  const purge = async () => {
    setBusy('purge');
    try {
      const res = await api.post<{ purged: string[] }>('/admin/cache/purge');
      const names = res.data?.purged ?? [];
      toast(`Purged ${names.length} Redis cache${names.length === 1 ? '' : 's'}.`, 'success');
    } catch { /* surfaced by interceptor */ }
    setBusy(null);
  };

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <div className="glass-card p-5">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground mb-1">
          <ShieldBan size={18} className="text-destructive" /> IP Blacklist
        </h2>
        <p className="text-sm text-muted-foreground mb-4">Block or unblock a client IP at the security filter (Redis <span className="font-mono text-xs">blacklist:ips</span>).</p>
        <input value={ip} onChange={(e) => setIp(e.target.value)} placeholder="e.g. 203.0.113.42"
          className="w-full bg-background border border-border rounded-lg px-3 py-2 mb-3 outline-none focus:ring-2 focus:ring-primary text-foreground font-mono text-sm" />
        <div className="flex gap-2">
          <button disabled={busy !== null} onClick={() => blacklist('add')}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:bg-destructive/90 disabled:opacity-40">
            <Ban size={15} /> {busy === 'add' ? 'Blocking…' : 'Block IP'}
          </button>
          <button disabled={busy !== null} onClick={() => blacklist('remove')}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-muted/50 text-foreground text-sm font-medium hover:bg-muted/70 disabled:opacity-40">
            <Trash2 size={15} /> {busy === 'remove' ? 'Removing…' : 'Unblock IP'}
          </button>
        </div>
      </div>

      <div className="glass-card p-5">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground mb-1">
          <Eraser size={18} className="text-primary" /> Cache Purge
        </h2>
        <p className="text-sm text-muted-foreground mb-4">Clear all Redis-backed Spring caches (incidents, resources, dashboard). Audit, session and proximity data are never cached.</p>
        <button disabled={busy !== null} onClick={purge}
          className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-40">
          <Eraser size={15} /> {busy === 'purge' ? 'Purging…' : 'Purge all caches'}
        </button>
      </div>
    </div>
  );
};
