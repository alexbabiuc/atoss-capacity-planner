<template>
  <div>
    <RouterLink :to="epic?.initiativeId ? `/initiatives/${epic.initiativeId}` : '/initiatives'" class="back-link">
      ← {{ epic?.initiativeId ? 'Initiative' : 'Initiatives' }}
    </RouterLink>

    <div v-if="loading" class="loading">Loading…</div>
    <div v-else-if="!epic" class="empty">Epic not found.</div>

    <template v-else>
      <div class="page-header">
        <h1>{{ epic.name }}</h1>
        <span :class="['tag', priorityClass(epic.priority)]">{{ epic.priority }}</span>
        <span :class="['tag', statusClass(epic.status)]">{{ epic.status }}</span>
      </div>

      <!-- Risk banner -->
      <div v-if="epic.isAtRisk" class="risk-banner">
        ⚠ This epic is at risk — see shortfalls below.
      </div>

      <!-- Meta row -->
      <div class="meta-row card">
        <div class="meta-item">
          <span class="meta-label">Estimate</span>
          <span class="meta-value">{{ epic.estimatePd }} pd</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">Start</span>
          <span class="meta-value">{{ epic.startDate }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">Due</span>
          <span class="meta-value">{{ epic.dueDate }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">Team</span>
          <span class="meta-value">{{ teamName(epic.teamId) }}</span>
        </div>
      </div>

      <!-- Skill shortfalls -->
      <div class="card" style="margin-top:1rem; overflow-x:auto">
        <div class="section-title">Skill shortfalls</div>
        <div v-if="!epic.skillShortfalls.length" class="empty" style="padding:1rem 0">
          No skill shortfalls detected.
        </div>
        <table v-else>
          <thead>
            <tr>
              <th>Skill</th>
              <th>Min. proficiency</th>
              <th>Demand (pd)</th>
              <th>Available (pd)</th>
              <th>Gap (pd)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="sf in epic.skillShortfalls" :key="sf.skillId">
              <td>{{ sf.skillName }}</td>
              <td><span class="tag tag-blue">{{ sf.minProficiency }}</span></td>
              <td>{{ sf.demandPd }}</td>
              <td>{{ sf.availablePd }}</td>
              <td><span class="tag tag-red">-{{ sf.gapPd }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Timeline bar -->
      <div class="card" style="margin-top:1rem">
        <div class="section-title">Timeline</div>
        <div class="timeline-bar-wrap">
          <div class="timeline-bar" :style="timelineStyle" />
        </div>
        <div class="timeline-labels">
          <span>{{ epic.startDate }}</span>
          <span>{{ epic.dueDate }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { epicsApi } from '@/api/client'
import { usePlanningStore } from '@/stores/planning'
import type { EpicSummary, EpicPriority, EpicStatus } from '@/types'

const route = useRoute()
const planning = usePlanningStore()
const epic = ref<EpicSummary | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await epicsApi.get(route.params.id as string)
    epic.value = data
  } finally {
    loading.value = false
  }
})

const timelineStyle = computed(() => ({
  background: epic.value?.isAtRisk ? 'var(--c-red)' : 'var(--c-teal)',
}))

function teamName(id: string) {
  return planning.teams.find(t => t.id === id)?.name ?? id.slice(0, 8)
}

function priorityClass(p: EpicPriority) {
  const map: Record<EpicPriority, string> = {
    MUST: 'tag-red', SHOULD: 'tag-amber', COULD: 'tag-blue', WONT: 'tag-gray',
  }
  return map[p]
}

function statusClass(s: EpicStatus) {
  const map: Record<EpicStatus, string> = {
    COMMITTED: 'tag-green', AT_RISK: 'tag-amber', DEFERRED: 'tag-gray', DONE: 'tag-blue',
  }
  return map[s]
}
</script>

<style scoped>
.back-link { font-size: .875rem; color: var(--c-muted); display: inline-block; margin-bottom: 1rem; }
.page-header { display: flex; align-items: center; gap: .75rem; margin-bottom: .75rem; }
.page-header h1 { font-size: 1.25rem; font-weight: 700; }

.risk-banner {
  background: #fee2e2; border: 1px solid var(--c-red); color: #991b1b;
  padding: .75rem 1rem; border-radius: 6px; margin-bottom: 1rem; font-weight: 500;
}

.meta-row { display: flex; gap: 2rem; flex-wrap: wrap; }
.meta-item { display: flex; flex-direction: column; gap: 2px; }
.meta-label { font-size: .7rem; text-transform: uppercase; color: var(--c-muted); }
.meta-value { font-size: .875rem; font-weight: 600; }

.timeline-bar-wrap {
  height: 12px; background: var(--c-border); border-radius: 6px; overflow: hidden; margin-bottom: .25rem;
}
.timeline-bar { height: 100%; width: 100%; border-radius: 6px; }
.timeline-labels { display: flex; justify-content: space-between; font-size: .75rem; color: var(--c-muted); }
</style>
