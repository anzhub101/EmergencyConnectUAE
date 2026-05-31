// Shared types mirroring the Spring Boot backend DTOs (SRS-conformant contract).

export type IncidentStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
export type Criticality = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface Incident {
  id: string;
  description: string;
  status: IncidentStatus;
  criticality: Criticality | null;
  latitude: number | null;
  longitude: number | null;
  reportedBy: string | null;
  priorityScore: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface Paged<T> {
  data: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface TriageResult {
  criticality: Criticality;
  confidence: number;
  recommendedUnits: string[];
  recommendedHospitalTier: string;
  matchedKeywords: string[];
  dispatchCount: number;
}

export interface ProximityUnit {
  id: string;
  name: string | null;
  type: string;
  status: string;
  distanceMetres: number;
  estimatedArrivalMinutes: number;
}

export interface ResourceAvailability {
  id: string;
  name: string;
  emirate: string;
  totalBeds: number;
  availableBeds: number;
  icuAvailable: number;
}

export interface AuditLog {
  id: string;
  userId: string | null;
  userEmail: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  timestamp: string;
  ipAddress: string | null;
  result: string;
}

export interface DashboardSummary {
  activeIncidents: number;
  incidentsByStatus: Record<string, number>;
  totalUnits: number;
  availableUnits: number;
  bedsByEmirate: Record<string, { availableBeds: number; icuAvailable: number }>;
}
