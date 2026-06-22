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

// ── Teams ─────────────────────────────────────────────────────────────────────

export const teamsApi = {
  list: (granularity: Granularity, from: string, to: string) =>
    http.get<TeamSummary[]>('/teams', { params: { granularity, from, to } }),

  get: (id: string, granularity: Granularity, from: string, to: string) =>
    http.get<TeamSummary>(`/teams/${id}`, { params: { granularity, from, to } }),

  create: (team: Partial<TeamSummary>) =>
    http.post<TeamSummary>('/teams', team),

  update: (id: string, patch: Partial<TeamSummary>) =>
    http.put<TeamSummary>(`/teams/${id}`, patch),
}

// ── People ────────────────────────────────────────────────────────────────────

export const peopleApi = {
  list: (teamId?: string) =>
    http.get<Person[]>('/people', { params: teamId ? { teamId } : {} }),

  get: (id: string) =>
    http.get<Person>(`/people/${id}`),

  create: (person: Partial<Person>) =>
    http.post<Person>('/people', person),

  update: (id: string, patch: Partial<Person>) =>
    http.put<Person>(`/people/${id}`, patch),

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

  create: (initiative: Partial<InitiativeSummary>) =>
    http.post<InitiativeSummary>('/initiatives', initiative),

  update: (id: string, patch: Partial<InitiativeSummary>) =>
    http.put<InitiativeSummary>(`/initiatives/${id}`, patch),
}

// ── Epics ─────────────────────────────────────────────────────────────────────

export const epicsApi = {
  list: (params?: { teamId?: string; initiativeId?: string }) =>
    http.get<EpicSummary[]>('/epics', { params }),

  get: (id: string) =>
    http.get<EpicSummary>(`/epics/${id}`),

  create: (epic: Partial<EpicSummary>) =>
    http.post<EpicSummary>('/epics', epic),

  update: (id: string, patch: Partial<EpicSummary>) =>
    http.put<EpicSummary>(`/epics/${id}`, patch),

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
