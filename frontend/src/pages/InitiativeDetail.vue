<template>
  <div>
    <RouterLink to="/initiatives" class="back-link">← Initiatives</RouterLink>

    <div v-if="loading" class="loading">Loading…</div>
    <div v-else-if="!initiative" class="empty">Initiative not found.</div>

    <template v-else>
      <div class="page-header">
        <h1>{{ initiative.name }}</h1>
        <span :class="['tag', statusClass(initiative.status)]">{{ initiative.status }}</span>
        <div style="flex:1" />
        <button class="btn btn-outline btn-sm" @click="openEditInitiative">Edit</button>
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
            <span :class="['tag', flag.severity === 'RED' ? 'tag-red' : 'tag-amber']">{{ flag.severity }}</span>
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
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:.75rem">
          <div class="section-title" style="margin:0">Epics</div>
          <button class="btn btn-primary btn-sm" @click="openAddEpic">+ Add Epic</button>
        </div>
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
              <th></th>
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
              <td style="text-align:right">
                <button class="btn btn-outline btn-sm" @click="openEditEpic(epic)">Edit</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Edit Initiative modal -->
    <div v-if="iniModal" class="modal-overlay" @click.self="iniModal = false">
      <div class="modal-card">
        <h2 class="modal-title">Edit Initiative</h2>
        <form @submit.prevent="saveInitiative">
          <div class="field">
            <label>Name *</label>
            <input v-model="iniForm.name" required />
          </div>
          <div class="field">
            <label>Description</label>
            <textarea v-model="iniForm.description" />
          </div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
            <div class="field" style="margin:0">
              <label>Top-down estimate (pd) *</label>
              <input v-model.number="iniForm.topDownEstimate" type="number" min="0" step="1" required />
            </div>
            <div class="field" style="margin:0">
              <label>Priority <span class="hint">(lower = higher)</span></label>
              <input v-model.number="iniForm.priority" type="number" min="1" />
            </div>
            <div class="field" style="margin:0">
              <label>Start date</label>
              <input v-model="iniForm.startDate" type="date" />
            </div>
            <div class="field" style="margin:0">
              <label>Target delivery *</label>
              <input v-model="iniForm.targetDeliveryDate" type="date" required />
            </div>
            <div class="field" style="margin:0">
              <label>Status</label>
              <select v-model="iniForm.status">
                <option>PROPOSED</option><option>COMMITTED</option>
                <option>AT_RISK</option><option>DELIVERED</option><option>CANCELLED</option>
              </select>
            </div>
            <div class="field" style="margin:0">
              <label>Owner name</label>
              <input v-model="iniForm.ownerName" />
            </div>
          </div>
          <div v-if="iniErr" class="form-error" style="margin-top:.875rem">{{ iniErr }}</div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="iniModal = false">Cancel</button>
            <button type="submit" class="btn btn-primary" :disabled="iniSaving">
              {{ iniSaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Add / Edit Epic modal -->
    <div v-if="epicModal" class="modal-overlay" @click.self="epicModal = false">
      <div class="modal-card">
        <h2 class="modal-title">{{ epicForm.id ? 'Edit Epic' : 'Add Epic' }}</h2>
        <form @submit.prevent="saveEpic">
          <div class="field">
            <label>Name *</label>
            <input v-model="epicForm.name" required placeholder="e.g. SEPA Instant API" />
          </div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
            <div class="field" style="margin:0">
              <label>Team *</label>
              <select v-model="epicForm.teamId" required>
                <option value="" disabled>Select team…</option>
                <option v-for="t in planning.teams" :key="t.id" :value="t.id">{{ t.name }}</option>
              </select>
            </div>
            <div class="field" style="margin:0">
              <label>Estimate (pd) *</label>
              <input v-model.number="epicForm.estimate" type="number" min="0" step="1" required />
            </div>
            <div class="field" style="margin:0">
              <label>Priority</label>
              <select v-model="epicForm.priority">
                <option>MUST</option><option>SHOULD</option><option>COULD</option><option>WONT</option>
              </select>
            </div>
            <div class="field" style="margin:0">
              <label>Status</label>
              <select v-model="epicForm.status">
                <option>COMMITTED</option><option>AT_RISK</option><option>DEFERRED</option><option>DONE</option>
              </select>
            </div>
            <div class="field" style="margin:0">
              <label>Start date *</label>
              <input v-model="epicForm.startDate" type="date" required />
            </div>
            <div class="field" style="margin:0">
              <label>Due date *</label>
              <input v-model="epicForm.dueDate" type="date" required />
            </div>
          </div>
          <div v-if="epicErr" class="form-error" style="margin-top:.875rem">{{ epicErr }}</div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="epicModal = false">Cancel</button>
            <button type="submit" class="btn btn-primary" :disabled="epicSaving">
              {{ epicSaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { initiativesApi, epicsApi } from '@/api/client'
import { usePlanningStore } from '@/stores/planning'
import type { InitiativeSummary, EpicSummary, InitiativeStatus, EpicPriority, EpicStatus } from '@/types'

const route    = useRoute()
const planning = usePlanningStore()

const initiative = ref<InitiativeSummary | null>(null)
const epics      = ref<EpicSummary[]>([])
const loading    = ref(true)

onMounted(async () => {
  const id = route.params.id as string
  try {
    const [ini, ep] = await Promise.all([
      initiativesApi.get(id),
      epicsApi.list({ initiativeId: id }),
    ])
    initiative.value = ini.data
    epics.value      = ep.data
  } finally {
    loading.value = false
  }
})

async function reload() {
  const id = route.params.id as string
  const [ini, ep] = await Promise.all([
    initiativesApi.get(id),
    epicsApi.list({ initiativeId: id }),
  ])
  initiative.value = ini.data
  epics.value      = ep.data
}

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

function teamName(id: string) { return planning.teams.find(t => t.id === id)?.name ?? id.slice(0, 8) }

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

// ── Edit Initiative ──────────────────────────────────────────────────────────

const iniModal  = ref(false)
const iniSaving = ref(false)
const iniErr    = ref('')
const iniForm   = ref({
  name: '', description: '', topDownEstimate: 0, priority: 10,
  startDate: '', targetDeliveryDate: '', status: 'PROPOSED' as InitiativeStatus, ownerName: '',
})

function openEditInitiative() {
  if (!initiative.value) return
  const ini = initiative.value
  iniForm.value = {
    name: ini.name,
    description: ini.description ?? '',
    topDownEstimate: ini.topDownEstimatePd,
    priority: ini.priority,
    startDate: ini.startDate ?? '',
    targetDeliveryDate: ini.targetDeliveryDate,
    status: ini.status,
    ownerName: ini.ownerName ?? '',
  }
  iniErr.value = ''
  iniModal.value = true
}

async function saveInitiative() {
  if (!initiative.value) return
  iniSaving.value = true
  iniErr.value = ''
  try {
    await initiativesApi.update(initiative.value.id, {
      name: iniForm.value.name,
      description: iniForm.value.description || undefined,
      topDownEstimate: iniForm.value.topDownEstimate,
      priority: iniForm.value.priority,
      startDate: iniForm.value.startDate || undefined,
      targetDeliveryDate: iniForm.value.targetDeliveryDate,
      status: iniForm.value.status,
      ownerName: iniForm.value.ownerName || undefined,
    })
    iniModal.value = false
    await reload()
    await planning.loadAll()
  } catch (e: any) {
    iniErr.value = e?.response?.data?.message ?? 'Save failed.'
  } finally {
    iniSaving.value = false
  }
}

// ── Add / Edit Epic ──────────────────────────────────────────────────────────

const epicModal  = ref(false)
const epicSaving = ref(false)
const epicErr    = ref('')
const blankEpicForm = () => ({
  id: '', name: '', teamId: '', estimate: 0,
  priority: 'MUST' as EpicPriority, status: 'COMMITTED' as EpicStatus,
  startDate: '', dueDate: '',
})
const epicForm = ref(blankEpicForm())

function openAddEpic() {
  epicForm.value = blankEpicForm()
  epicErr.value = ''
  epicModal.value = true
}

function openEditEpic(epic: EpicSummary) {
  epicForm.value = {
    id: epic.id,
    name: epic.name,
    teamId: epic.teamId,
    estimate: epic.estimatePd,
    priority: epic.priority,
    status: epic.status,
    startDate: epic.startDate,
    dueDate: epic.dueDate,
  }
  epicErr.value = ''
  epicModal.value = true
}

async function saveEpic() {
  epicSaving.value = true
  epicErr.value = ''
  try {
    const payload = {
      name: epicForm.value.name,
      teamId: epicForm.value.teamId,
      initiativeId: initiative.value?.id,
      estimate: epicForm.value.estimate,
      priority: epicForm.value.priority,
      status: epicForm.value.status,
      startDate: epicForm.value.startDate,
      dueDate: epicForm.value.dueDate,
    }
    if (epicForm.value.id) {
      await epicsApi.update(epicForm.value.id, payload)
    } else {
      await epicsApi.create(payload)
    }
    epicModal.value = false
    await reload()
    await planning.loadAll()
  } catch (e: any) {
    epicErr.value = e?.response?.data?.message ?? 'Save failed.'
  } finally {
    epicSaving.value = false
  }
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
.btn-sm { font-size: .8rem; padding: 3px 10px; }
</style>
