import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';
import api from '../lib/api';
import { useAuth } from '../context/AuthContext';
import { toast } from '../lib/toast';
import {
  Activity,
  Truck,
  AlertTriangle,
  ChevronRight,
  Siren,
  Sparkles,
  Brain,
  X,
  Loader2
} from 'lucide-react';
import type { DashboardSummary, Incident, Paged, TriageResult } from '../types';

const CRITICALITY_COLOR: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
};

export const Dashboard = () => {
  const { role } = useAuth();
  const isDispatcher = role === 'DISPATCHER' || role === 'SYSTEM_ADMIN';

  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);

  // Create Incident Form State
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [desc, setDesc] = useState('');
  const [lat, setLat] = useState('');
  const [lng, setLng] = useState('');
  const [overrideCriticality, setOverrideCriticality] = useState('');
  const [triaging, setTriaging] = useState(false);
  const [triageResult, setTriageResult] = useState<TriageResult | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loadData = useCallback(async () => {
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
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const resetForm = () => {
    setDesc('');
    setLat('');
    setLng('');
    setOverrideCriticality('');
    setTriageResult(null);
  };

  const handleRunTriage = async () => {
    if (!desc.trim()) return;
    setTriaging(true);
    try {
      const { data } = await api.post<TriageResult>('/triage/analyze', { description: desc.trim() });
      setTriageResult(data);
      // Pre-fill the criticality field with AI suggestion as default
      setOverrideCriticality(data.criticality);
      toast('AI Triage analysis completed.', 'success');
    } catch {
      toast('AI Triage evaluation failed.', 'error');
    } finally {
      setTriaging(false);
    }
  };

  const handleSubmitIncident = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!desc.trim() || !lat || !lng) return;

    setSubmitting(true);
    try {
      await api.post('/incidents', {
        description: desc.trim(),
        latitude: parseFloat(lat),
        longitude: parseFloat(lng),
        criticality: overrideCriticality || null,
      });

      toast('Incident created and dispatched successfully.', 'success');
      setShowCreateModal(false);
      resetForm();
      setLoading(true);
      await loadData();
    } catch {
      /* surfaced by api interceptor */
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex h-screen bg-background text-foreground">
      <Sidebar />
      <main className="flex-1 overflow-auto p-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
          {isDispatcher && (
            <button
              onClick={() => setShowCreateModal(true)}
              className="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-red-600 text-white font-semibold hover:bg-red-500 transition-all active:scale-95 shadow-lg shadow-red-600/20"
            >
              <Siren size={18} className="animate-pulse" />
              New Incident
            </button>
          )}
        </div>

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

            <h2 className="text-lg font-semibold mb-3 text-foreground">Recent Incidents</h2>
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

      {/* Dispatch Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-fade-in">
          <div className="glass-card max-w-2xl w-full p-6 space-y-6 animate-zoom-in relative">
            <button
              onClick={() => {
                setShowCreateModal(false);
                resetForm();
              }}
              className="absolute top-4 right-4 text-muted-foreground hover:text-foreground p-1.5 rounded-full hover:bg-muted/50 transition-colors"
            >
              <X size={18} />
            </button>

            <div className="flex items-center gap-2 border-b border-border pb-3">
              <Siren className="text-red-500 animate-pulse" size={22} />
              <div>
                <h2 className="text-xl font-bold text-foreground">Log New 999 Incident</h2>
                <p className="text-xs text-muted-foreground">Report an emergency incident, run AI triage evaluation, and dispatch units.</p>
              </div>
            </div>

            <form onSubmit={handleSubmitIncident} className="space-y-4">
              {/* Call description */}
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-foreground">999 Call Transcript / Description</label>
                <textarea
                  required
                  rows={3}
                  value={desc}
                  onChange={(e) => setDesc(e.target.value)}
                  placeholder="e.g. Critical multi-vehicle collision on Sheikh Zayed Road near Dubai Marina. Severe fire, victims are trapped, breathing issues reported."
                  className="w-full px-3 py-2 rounded-lg bg-background border border-border text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/60 resize-none text-sm"
                />
              </div>

              {/* Coordinates */}
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-sm font-medium text-foreground">Latitude</label>
                  <input
                    type="number"
                    step="any"
                    required
                    value={lat}
                    onChange={(e) => setLat(e.target.value)}
                    placeholder="e.g. 25.0805"
                    className="w-full px-3 py-2 rounded-lg bg-background border border-border text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/60 text-sm font-mono"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-sm font-medium text-foreground">Longitude</label>
                  <input
                    type="number"
                    step="any"
                    required
                    value={lng}
                    onChange={(e) => setLng(e.target.value)}
                    placeholder="e.g. 55.1403"
                    className="w-full px-3 py-2 rounded-lg bg-background border border-border text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/60 text-sm font-mono"
                  />
                </div>
              </div>

              {/* Prefill UAE locations button */}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setLat('25.0805');
                    setLng('55.1403');
                  }}
                  className="px-2.5 py-1 rounded bg-muted/50 text-[11px] text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                >
                  📍 Dubai Marina
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setLat('24.4539');
                    setLng('54.3773');
                  }}
                  className="px-2.5 py-1 rounded bg-muted/50 text-[11px] text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                >
                  📍 Abu Dhabi City
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setLat('25.2921');
                    setLng('55.3892');
                  }}
                  className="px-2.5 py-1 rounded bg-muted/50 text-[11px] text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                >
                  📍 Sharjah Corniche
                </button>
              </div>

              {/* Triage Trigger */}
              <div className="flex items-center justify-between pt-2">
                <button
                  type="button"
                  disabled={!desc.trim() || triaging}
                  onClick={handleRunTriage}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary/20 text-primary hover:bg-primary/30 transition-all text-xs font-semibold disabled:opacity-40"
                >
                  {triaging ? <Loader2 size={13} className="animate-spin" /> : <Brain size={13} />}
                  Analyze with AI Triage Engine
                </button>

                {triageResult && (
                  <span className="text-[11px] text-muted-foreground font-medium flex items-center gap-1">
                    <Sparkles size={11} className="text-yellow-400" />
                    AI confidence: {(triageResult.confidence * 100).toFixed(0)}%
                  </span>
                )}
              </div>

              {/* Triage Preview Panel */}
              {triageResult && (
                <div className="rounded-lg border border-primary/25 bg-primary/5 p-4 space-y-3 animate-slide-in">
                  <div className="flex items-center justify-between text-xs border-b border-primary/10 pb-1.5">
                    <span className="font-semibold text-primary flex items-center gap-1">
                      <Sparkles size={12} /> AI Recommendation
                    </span>
                    <span className="font-mono text-muted-foreground">Confidence: {triageResult.confidence}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-4 text-xs">
                    <div>
                      <p className="text-muted-foreground">Criticality:</p>
                      <p className={`font-bold mt-0.5 ${CRITICALITY_COLOR[triageResult.criticality] ?? 'text-foreground'}`}>
                        {triageResult.criticality}
                      </p>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Hospital Tier:</p>
                      <p className="font-semibold text-foreground mt-0.5">{triageResult.recommendedHospitalTier}</p>
                    </div>
                    <div className="col-span-2">
                      <p className="text-muted-foreground">Suggested Dispatch Units:</p>
                      <p className="font-semibold text-foreground mt-0.5">
                        {triageResult.recommendedUnits.join(', ') || 'None'}
                      </p>
                    </div>
                    {triageResult.matchedKeywords.length > 0 && (
                      <div className="col-span-2">
                        <p className="text-muted-foreground">Matched Keywords:</p>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {triageResult.matchedKeywords.map((kw, i) => (
                            <span key={i} className="px-1.5 py-0.5 rounded bg-muted text-[10px] text-muted-foreground font-mono">
                              {kw}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Dispatcher Override Dropdown */}
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-foreground">Criticality Level (AI Suggestion or Override)</label>
                <select
                  required
                  value={overrideCriticality}
                  onChange={(e) => setOverrideCriticality(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-background border border-border text-foreground focus:outline-none focus:ring-2 focus:ring-primary/60 text-sm"
                >
                  <option value="">-- Select Criticality Level --</option>
                  <option value="CRITICAL">🔴 CRITICAL</option>
                  <option value="HIGH">🟠 HIGH</option>
                  <option value="MEDIUM">🟡 MEDIUM</option>
                  <option value="LOW">🟢 LOW</option>
                </select>
              </div>

              {/* Submit Actions */}
              <div className="flex gap-3 justify-end pt-4 border-t border-border">
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => {
                    setShowCreateModal(false);
                    resetForm();
                  }}
                  className="px-4 py-2 rounded-lg bg-muted text-foreground text-sm font-medium hover:bg-muted/80 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting || !desc.trim() || !lat || !lng || !overrideCriticality}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-red-600 text-white text-sm font-semibold hover:bg-red-500 transition-all active:scale-95 disabled:opacity-55 disabled:active:scale-100 shadow-md shadow-red-600/10"
                >
                  {submitting && <Loader2 size={16} className="animate-spin" />}
                  File & Dispatch
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
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
