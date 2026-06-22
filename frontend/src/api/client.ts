// api/client.ts — thin axios wrapper; all API calls go through here

import axios from 'axios'
import type {
  TeamSummary, InitiativeSummary, EpicSummary, Person, Skill,
  ChangeSetSummary, ScenarioImpact, Granularity
} from '@/types'

const http = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  withCredentials: true,
  auth: { username: 'admin', password: 'admin' },
})

// ── Request types (match backend request DTOs exactly) ────────────────────────

export interface TeamRequest {
  name?: string
  overheadFactor?: number
  supportFactor?: number
}

export interface PersonSkillRequest {
  skillId: string
  proficiency: string
}

export interface PersonRequest {
  name?: string
  teamId?: string
  baseAvailability?: number
  skills?: PersonSkillRequest[]
}

export interface InitiativeRequest {
  name?: string
  description?: string
  topDownEstimate?: number
  startDate?: string
  targetDeliveryDate?: string
  priority?: number
  ownerName?: string
  status?: string
}

export interface SkillRequirementRequest {
  skillId: string
  minProficiency: string
  demandPd: number
}

export interface EpicRequest {
  name?: string
  teamId?: string
  initiativeId?: string
  estimate?: number
  startDate?: string
  dueDate?: string
  priority?: string
  status?: string
  requiredSkills?: SkillRequirementRequest[]
}

// ── Teams ─────────────────────────────────────────────────────────────────────

export const teamsApi = {
  list: (granularity: Granularity, from: string, to: string) =>
    http.get<TeamSummary[]>('/teams', { params: { granularity, from, to } }),

  get: (id: string, granularity: Granularity, from: string, to: string) =>
    http.get<TeamSummary>(`/teams/${id}`, { params: { granularity, from, to } }),

  create: (req: TeamRequest) =>
    http.post<TeamSummary>('/teams', req),

  update: (id: string, req: TeamRequest) =>
    http.put<TeamSummary>(`/teams/${id}`, req),
}

// ── People ────────────────────────────────────────────────────────────────────

export const peopleApi = {
  list: (teamId?: string) =>
    http.get<Person[]>('/people', { params: teamId ? { teamId } : {} }),

  get: (id: string) =>
    http.get<Person>(`/people/${id}`),

  create: (req: PersonRequest) =>
    http.post<Person>('/people', req),

  update: (id: string, req: PersonRequest) =>
    http.put<Person>(`/people/${id}`, req),

  addOverride: (id: string, override: Person['availabilityOverrides'][0]) =>
    http.post<Person>(`/people/${id}/availability-overrides`, override),

  removeOverride: (id: string, index: number) =>
    http.delete<Person>(`/people/${id}/availability-overrides/${index}`),
}

// ── Initiatives ───────────────────────────────────────────────────────────────

export const initiativesApi = {
  list: () =>
    http.get<InitiativeSummary[]>('/initiatives'),

  get: (id: string) =>
    http.get<InitiativeSummary>(`/initiatives/${id}`),

  create: (req: InitiativeRequest) =>
    http.post<InitiativeSummary>('/initiatives', req),

  update: (id: string, req: InitiativeRequest) =>
    http.put<InitiativeSummary>(`/initiatives/${id}`, req),
}

// ── Epics ─────────────────────────────────────────────────────────────────────

export const epicsApi = {
  list: (params?: { teamId?: string; initiativeId?: string }) =>
    http.get<EpicSummary[]>('/epics', { params }),

  get: (id: string) =>
    http.get<EpicSummary>(`/epics/${id}`),

  create: (req: EpicRequest) =>
    http.post<EpicSummary>('/epics', req),

  update: (id: string, req: EpicRequest) =>
    http.put<EpicSummary>(`/epics/${id}`, req),

  delete: (id: string) =>
    http.delete(`/epics/${id}`),
}

// ── Scenarios ─────────────────────────────────────────────────────────────────

export type Delta = { entityType: string; entityId: string; field: string; newValue: unknown }

export const scenariosApi = {
  preview: (deltas: Delta[]) =>
    http.post<ScenarioImpact>('/scenarios/preview', { deltas }),

  commit: (deltas: Delta[], reason: string, source: string) =>
    http.post<ChangeSetSummary>('/scenarios/commit', { deltas, reason, source }),
}

// ── Change log ────────────────────────────────────────────────────────────────

export const changeLogApi = {
  list: () =>
    http.get<ChangeSetSummary[]>('/changelog'),

  forEntity: (entityType: string, entityId: string) =>
    http.get<ChangeSetSummary[]>('/changelog/entity', { params: { entityType, entityId } }),
}
