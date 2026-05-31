import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';
import api from '../lib/api';
import { Activity, Truck, AlertTriangle, ChevronRight } from 'lucide-react';
import type { DashboardSummary, Incident, Paged } from '../types';

const CRITICALITY_COLOR: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
};

export const Dashboard = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [s, i] = await Promise.all([
          api.get<DashboardSummary>('/dashboard/summary'),
          api.get<Paged<Incident>>('/incidents?size=20'),
        ]);
        setSummary(s.data);
        setIncidents(i.data.data);
      } catch {
        /* errors surfaced via the api interceptor toast */
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <main className="flex-1 overflow-auto p-8">
        <h1 className="text-3xl font-bold tracking-tight text-foreground mb-6">Dashboard</h1>

        {loading ? (
          <p className="text-muted-foreground">Loading…</p>
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
              <StatCard
                icon={<Activity className="text-primary" size={20} />}
                label="Active Incidents"
                value={summary?.activeIncidents ?? 0}
              />
              <StatCard
                icon={<Truck className="text-primary" size={20} />}
                label="Available Units"
                value={`${summary?.availableUnits ?? 0} / ${summary?.totalUnits ?? 0}`}
              />
              <StatCard
                icon={<AlertTriangle className="text-primary" size={20} />}
                label="Open"
                value={summary?.incidentsByStatus?.OPEN ?? 0}
              />
            </div>

            <h2 className="text-lg font-semibold text-foreground mb-3">Recent Incidents</h2>
            <div className="glass-card divide-y divide-border overflow-hidden">
              {incidents.length === 0 && (
                <p className="p-4 text-sm text-muted-foreground">No incidents found.</p>
              )}
              {incidents.map((inc) => (
                <Link
                  key={inc.id}
                  to={`/incidents/${inc.id}`}
                  className="flex items-center gap-4 p-4 hover:bg-muted/50 transition-colors"
                >
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-foreground truncate">{inc.description}</p>
                    <p className="text-xs text-muted-foreground">{inc.status}</p>
                  </div>
                  {inc.criticality && (
                    <span className={`text-xs font-semibold ${CRITICALITY_COLOR[inc.criticality] ?? 'text-muted-foreground'}`}>
                      {inc.criticality}
                    </span>
                  )}
                  <ChevronRight size={16} className="text-muted-foreground" />
                </Link>
              ))}
            </div>
          </>
        )}
      </main>
    </div>
  );
};

const StatCard = ({ icon, label, value }: { icon: React.ReactNode; label: string; value: React.ReactNode }) => (
  <div className="glass-card p-5 flex items-center gap-4">
    <div className="rounded-lg bg-muted p-2.5">{icon}</div>
    <div>
      <p className="text-2xl font-bold text-foreground">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  </div>
);
