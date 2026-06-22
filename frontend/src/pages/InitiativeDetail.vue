<template>
  <div>
    <RouterLink to="/initiatives" class="back-link">← Initiatives</RouterLink>

    <div v-if="loading" class="loading">Loading…</div>
    <div v-else-if="!initiative" class="empty">Initiative not found.</div>

    <template v-else>
      <div class="page-header">
        <h1>{{ initiative.name }}</h1>
        <span :class="['tag', statusClass(initiative.status)]">{{ initiative.status }}</span>
      </div>

      <p v-if="initiative.description" class="description">{{ initiative.description }}</p>

      <!-- Gap meter -->
      <div class="card gap-card">
        <div class="gap-header">
          <span class="gap-label">Decomposition gap</span>
          <span :class="['tag', initiative.decompositionGapPd > 0 ? 'tag-amber' : 'tag-green']">
            {{ initiative.decompositionGapPd > 0 ? '+' : '' }}{{ initiative.decompositionGapPd }} pd
          </span>
        </div>
        <div class="gap-row">
          <span class="gap-meta">Top-down: {{ initiative.topDownEstimatePd }} pd</span>
          <span class="gap-meta">Bottom-up: {{ initiative.bottomUpEstimatePd }} pd</span>
        </div>
        <div class="meter-track">
          <div class="meter-fill" :style="meterStyle" />
        </div>
      </div>

      <!-- Flags -->
      <div v-if="initiative.flags.length" class="card" style="margin-top:1rem">
        <div class="section-title" style="margin-bottom:.5rem">Risk flags</div>
        <div class="flags-list">
          <div v-for="flag in initiative.flags" :key="flag.type + flag.entityId" class="flag-item">
            <span :class="['tag', flag.severity === 'RED' ? 'tag-red' : 'tag-amber']">
              {{ flag.severity }}
            </span>
            <span>{{ flag.message }}</span>
          </div>
        </div>
      </div>

      <!-- Teams involved -->
      <div v-if="initiative.teamsInvolved.length" class="card" style="margin-top:1rem">
        <div class="section-title" style="margin-bottom:.5rem">Teams involved</div>
        <div class="teams-list">
          <span v-for="teamId in initiative.teamsInvolved" :key="teamId" class="tag tag-blue">
            {{ teamName(teamId) }}
          </span>
        </div>
      </div>

      <!-- Epics -->
      <div class="card" style="margin-top:1rem; overflow-x:auto">
        <div class="section-title">Epics</div>
        <div v-if="!epics.length" class="empty" style="padding:1rem 0">No epics.</div>
        <table v-else>
          <thead>
            <tr>
              <th>Name</th>
              <th>Team</th>
              <th>Priority</th>
              <th>Status</th>
              <th>Estimate (pd)</th>
              <th>Dates</th>
              <th>Risk</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="epic in epics" :key="epic.id">
              <td><RouterLink :to="`/epics/${epic.id}`">{{ epic.name }}</RouterLink></td>
              <td>{{ teamName(epic.teamId) }}</td>
              <td><span :class="['tag', priorityClass(epic.priority)]">{{ epic.priority }}</span></td>
              <td><span :class="['tag', epicStatusClass(epic.status)]">{{ epic.status }}</span></td>
              <td>{{ epic.estimatePd }}</td>
              <td style="white-space:nowrap">{{ epic.startDate }} – {{ epic.dueDate }}</td>
              <td><span v-if="epic.isAtRisk" class="tag tag-red">At risk</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { initiativesApi, epicsApi } from '@/api/client'
import { usePlanningStore } from '@/stores/planning'
import type { InitiativeSummary, EpicSummary, InitiativeStatus, EpicPriority, EpicStatus } from '@/types'

const route = useRoute()
const planning = usePlanningStore()
const initiative = ref<InitiativeSummary | null>(null)
const epics = ref<EpicSummary[]>([])
const loading = ref(true)

onMounted(async () => {
  const id = route.params.id as string
  try {
    const [ini, ep] = await Promise.all([
      initiativesApi.get(id),
      epicsApi.list({ initiativeId: id }),
    ])
    initiative.value = ini.data
    epics.value = ep.data
  } finally {
    loading.value = false
  }
})

const meterStyle = computed(() => {
  if (!initiative.value) return {}
  const total = initiative.value.topDownEstimatePd
  if (!total) return { width: '0%' }
  const fill = Math.min(initiative.value.bottomUpEstimatePd / total, 1)
  return {
    width: `${fill * 100}%`,
    background: initiative.value.decompositionGapPd > 0 ? 'var(--c-amber)' : 'var(--c-teal)',
  }
})

function teamName(id: string) {
  return planning.teams.find(t => t.id === id)?.name ?? id.slice(0, 8)
}

function statusClass(s: InitiativeStatus) {
  const map: Record<InitiativeStatus, string> = {
    PROPOSED: 'tag-blue', COMMITTED: 'tag-green',
    AT_RISK: 'tag-amber', DELIVERED: 'tag-gray', CANCELLED: 'tag-gray',
  }
  return map[s]
}

function priorityClass(p: EpicPriority) {
  const map: Record<EpicPriority, string> = {
    MUST: 'tag-red', SHOULD: 'tag-amber', COULD: 'tag-blue', WONT: 'tag-gray',
  }
  return map[p]
}

function epicStatusClass(s: EpicStatus) {
  const map: Record<EpicStatus, string> = {
    COMMITTED: 'tag-green', AT_RISK: 'tag-amber', DEFERRED: 'tag-gray', DONE: 'tag-blue',
  }
  return map[s]
}
</script>

<style scoped>
.back-link { font-size: .875rem; color: var(--c-muted); display: inline-block; margin-bottom: 1rem; }
.page-header { display: flex; align-items: center; gap: .75rem; margin-bottom: .5rem; }
.page-header h1 { font-size: 1.25rem; font-weight: 700; }
.description { color: var(--c-muted); font-size: .875rem; margin-bottom: 1rem; }

.gap-card { margin-top: 1rem; }
.gap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: .5rem; }
.gap-label { font-weight: 600; }
.gap-row { display: flex; gap: 1.5rem; font-size: .8rem; color: var(--c-muted); margin-bottom: .5rem; }
.meter-track { height: 10px; background: var(--c-border); border-radius: 5px; overflow: hidden; }
.meter-fill { height: 100%; border-radius: 5px; transition: width .3s; }

.flags-list { display: flex; flex-direction: column; gap: .5rem; }
.flag-item { display: flex; align-items: center; gap: .5rem; font-size: .875rem; }
.teams-list { display: flex; gap: .5rem; flex-wrap: wrap; }
</style>
