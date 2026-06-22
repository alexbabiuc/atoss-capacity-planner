// stores/planning.ts — central state for the planning view

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { teamsApi, initiativesApi, epicsApi, changeLogApi, scenariosApi, type Delta } from '@/api/client'
import type { TeamSummary, InitiativeSummary, EpicSummary, ChangeSetSummary, ScenarioImpact, Granularity } from '@/types'

export const usePlanningStore = defineStore('planning', () => {

  // ── State ──────────────────────────────────────────────────────────────────

  const teams          = ref<TeamSummary[]>([])
  const initiatives    = ref<InitiativeSummary[]>([])
  const epics          = ref<EpicSummary[]>([])
  const changeLog      = ref<ChangeSetSummary[]>([])

  const granularity    = ref<Granularity>('MONTH')
  const horizonStart   = ref<string>(new Date().toISOString().slice(0, 10))
  const horizonEnd     = ref<string>(
    new Date(new Date().setFullYear(new Date().getFullYear() + 1)).toISOString().slice(0, 10)
  )

  const loading        = ref(false)
  const error          = ref<string | null>(null)

  // ── Derived ────────────────────────────────────────────────────────────────

  const redFlags    = computed(() => teams.value.flatMap(t => t.flags.filter(f => f.severity === 'RED')))
  const amberFlags  = computed(() => teams.value.flatMap(t => t.flags.filter(f => f.severity === 'AMBER')))
  const hasRisks    = computed(() => redFlags.value.length > 0 || amberFlags.value.length > 0)

  // ── Loaders ────────────────────────────────────────────────────────────────

  async function loadAll() {
    loading.value = true
    error.value = null
    try {
      const [t, i, e, cl] = await Promise.all([
        teamsApi.list(granularity.value, horizonStart.value, horizonEnd.value),
        initiativesApi.list(),
        epicsApi.list(),
        changeLogApi.list(),
      ])
      teams.value       = t.data
      initiatives.value = i.data
      epics.value       = e.data
      changeLog.value   = cl.data
    } catch (err: any) {
      error.value = err.message
    } finally {
      loading.value = false
    }
  }

  async function reloadTeams() {
    const { data } = await teamsApi.list(granularity.value, horizonStart.value, horizonEnd.value)
    teams.value = data
  }

  function setGranularity(g: Granularity) {
    granularity.value = g
    reloadTeams()
  }

  return {
    teams, initiatives, epics, changeLog,
    granularity, horizonStart, horizonEnd,
    loading, error,
    redFlags, amberFlags, hasRisks,
    loadAll, reloadTeams, setGranularity,
  }
})

// ── Scenario store ─────────────────────────────────────────────────────────────

export const useScenarioStore = defineStore('scenario', () => {

  const pendingDeltas  = ref<Delta[]>([])
  const impact         = ref<ScenarioImpact | null>(null)
  const previewLoading = ref(false)

  function addDelta(delta: Delta) {
    // Replace any existing delta for the same entity+field
    const idx = pendingDeltas.value.findIndex(
      d => d.entityType === delta.entityType &&
           d.entityId   === delta.entityId   &&
           d.field       === delta.field
    )
    if (idx >= 0) pendingDeltas.value.splice(idx, 1, delta)
    else pendingDeltas.value.push(delta)
  }

  function clearDeltas() {
    pendingDeltas.value = []
    impact.value = null
  }

  async function preview() {
    if (pendingDeltas.value.length === 0) return
    previewLoading.value = true
    try {
      const { data } = await scenariosApi.preview(pendingDeltas.value)
      impact.value = data
    } finally {
      previewLoading.value = false
    }
  }

  async function commit(reason: string, source: string) {
    if (!reason.trim()) throw new Error('Reason is required')
    await scenariosApi.commit(pendingDeltas.value, reason, source)
    clearDeltas()
    // Caller should trigger usePlanningStore().loadAll() to refresh
  }

  return { pendingDeltas, impact, previewLoading, addDelta, clearDeltas, preview, commit }
})
