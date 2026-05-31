import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';
import { ArrowLeft, Play, CheckCircle2, Truck, AlertTriangle, MapPin } from 'lucide-react';
import api from '../lib/api';
import { useAuth } from '../context/AuthContext';
import type { Incident, ProximityUnit } from '../types';

export const IncidentDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { role } = useAuth();
  const [incident, setIncident] = useState<Incident | null>(null);
  const [units, setUnits] = useState<ProximityUnit[]>([]);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const isDispatcher = role === 'DISPATCHER' || role === 'SYSTEM_ADMIN';

  const loadIncident = useCallback(async () => {
    if (!id) return;
    const { data } = await api.get<Incident>(`/incidents/${id}`);
    setIncident(data);
    if (data.latitude != null && data.longitude != null) {
      try {
        const { data: nearby } = await api.get<ProximityUnit[]>(
          `/resources/proximity?lat=${data.latitude}&lng=${data.longitude}&radius=50000&type=ALL`,
          { skipErrorToast: true });
        setUnits(nearby);
      } catch { /* proximity is best-effort */ }
    }
  }, [id]);

  useEffect(() => { loadIncident(); }, [loadIncident]);

  const transition = async (status: 'IN_PROGRESS' | 'RESOLVED') => {
    setBusy(true); setMessage(null);
    try {
      await api.put(`/incidents/${id}/status`, { status });
      setMessage({ type: 'ok', text: `Status updated to ${status}` });
      await loadIncident();
    } catch (e: any) {
      setMessage({ type: 'err', text: e?.response?.data?.message ?? 'Transition rejected' });
    }
    setBusy(false);
  };

  const assign = async (unitId: string) => {
    setBusy(true); setMessage(null);
    try {
      await api.post('/assignments', { incidentId: id, unitId });
      setMessage({ type: 'ok', text: 'Unit assigned successfully.' });
      await loadIncident();
    } catch (e: any) {
      const code = e?.response?.data?.error;
      setMessage({
        type: 'err',
        text: code === 'RESOURCE_LOCKED'
          ? '409 Conflict — unit is locked / already being dispatched.'
          : (e?.response?.data?.message ?? 'Assignment failed'),
      });
    }
    setBusy(false);
  };

  if (!incident) {
    return (
      <div className="flex h-screen bg-background">
        <Sidebar />
        <main className="flex-1 flex items-center justify-center text-muted-foreground">Loading incident...</main>
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <main className="flex-1 overflow-auto p-8">
        <button onClick={() => navigate('/')} className="flex items-center gap-2 text-muted-foreground hover:text-foreground mb-6">
          <ArrowLeft size={18} /> Back to dashboard
        </button>

        {message && (
          <div className={`mb-6 px-4 py-3 rounded-lg text-sm flex items-center gap-2 border ${
            message.type === 'ok' ? 'bg-green-500/10 border-green-500/40 text-green-500'
                                  : 'bg-destructive/15 border-destructive/50 text-destructive'}`}>
            <AlertTriangle size={16} /> {message.text}
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Left: incident info + transitions */}
          <div className="glass-card p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h1 className="text-xl font-bold text-foreground">Incident</h1>
              <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-primary/20 text-primary">{incident.criticality}</span>
            </div>
            <p className="text-foreground">{incident.description}</p>
            <div className="text-sm text-muted-foreground space-y-1">
              <p>Status: <span className="text-foreground font-medium">{incident.status}</span></p>
              <p>Priority score: <span className="text-foreground font-mono">{Math.round(incident.priorityScore ?? 0)}</span></p>
              {incident.latitude != null && (
                <p className="flex items-center gap-1"><MapPin size={14} /> {incident.latitude?.toFixed(4)}, {incident.longitude?.toFixed(4)}</p>
              )}
            </div>
            {isDispatcher && (
              <div className="flex gap-3 pt-2">
                <button disabled={busy || incident.status !== 'OPEN'} onClick={() => transition('IN_PROGRESS')}
                  className="flex items-center gap-2 px-4 py-2 rounded-lg bg-orange-500/15 text-orange-500 font-medium disabled:opacity-40 hover:bg-orange-500/25">
                  <Play size={16} /> Start
                </button>
                <button disabled={busy || incident.status !== 'IN_PROGRESS'} onClick={() => transition('RESOLVED')}
                  className="flex items-center gap-2 px-4 py-2 rounded-lg bg-green-500/15 text-green-500 font-medium disabled:opacity-40 hover:bg-green-500/25">
                  <CheckCircle2 size={16} /> Resolve
                </button>
              </div>
            )}
          </div>

          {/* Right: proximity units + assign */}
          <div className="glass-card p-0 overflow-hidden">
            <div className="p-4 border-b border-border bg-muted/30">
              <h3 className="font-semibold text-foreground">Nearest Available Units (PostGIS)</h3>
            </div>
            {units.length === 0 ? (
              <div className="p-6 text-center text-muted-foreground text-sm">No available units within range.</div>
            ) : (
              <ul className="divide-y divide-border/50">
                {units.map(u => (
                  <li key={u.id} className="flex items-center justify-between px-5 py-3">
                    <div>
                      <p className="text-foreground font-medium flex items-center gap-2"><Truck size={16} /> {u.type}</p>
                      <p className="text-xs text-muted-foreground">
                        {(u.distanceMetres / 1000).toFixed(1)} km · ETA {u.estimatedArrivalMinutes} min{u.name ? ` · ${u.name}` : ''}
                      </p>
                    </div>
                    {isDispatcher && (
                      <button disabled={busy} onClick={() => assign(u.id)}
                        className="px-3 py-1.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-40">
                        Assign
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};
