<template>
  <div>
    <h1 class="section-title">Scenario Builder</h1>

    <div class="layout">
      <!-- Left: delta editor -->
      <div class="left-panel">
        <div class="card">
          <div class="section-title">Add change</div>

          <div class="form-grid">
            <label class="form-label">Entity type
              <select v-model="form.entityType" @change="onTypeChange">
                <option value="TEAM">Team</option>
                <option value="EPIC">Epic</option>
                <option value="INITIATIVE">Initiative</option>
                <option value="PERSON">Person</option>
              </select>
            </label>
            <label class="form-label">{{ entityTypeLabel }}
              <select v-model="form.entityId" :disabled="loadingPeople" @change="onEntityChange">
                <option value="" disabled>
                  {{ loadingPeople ? 'Loading…' : `Select ${entityTypeLabel.toLowerCase()}…` }}
                </option>
                <option v-for="opt in entityOptions" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
              </select>
            </label>
          </div>

          <div style="margin-top:.5rem">
            <label class="form-label">Field
              <select v-model="form.field" :disabled="!form.entityId" @change="onFieldChange">
                <option value="" disabled>Select field…</option>
                <option v-for="f in fieldOptions" :key="f.field" :value="f.field">{{ f.label }}</option>
              </select>
            </label>
          </div>

          <div v-if="currentValue !== null && form.field" class="current-row">
            <span class="current-label">Current value:</span>
            <code class="current-val">{{ currentValue }}</code>
          </div>

          <div v-if="selectedFieldDef" style="margin-top:.5rem">
            <label class="form-label">New value
              <select v-if="selectedFieldDef.type === 'enum'" v-model="form.newValue">
                <option value="" disabled>Select…</option>
                <option v-for="o in selectedFieldDef.options" :key="o" :value="o">{{ o }}</option>
              </select>
              <input v-else-if="selectedFieldDef.type === 'date'"
                v-model="form.newValue" type="date" />
              <input v-else v-model.number="form.newValue" type="number"
                :min="selectedFieldDef.min" :max="selectedFieldDef.max"
                :step="selectedFieldDef.step ?? 1" />
            </label>
          </div>

          <button class="btn btn-primary" style="margin-top:.75rem"
            :disabled="!canAddDelta" @click="addDelta">
            + Add to scenario
          </button>
        </div>

        <!-- Pending deltas -->
        <div class="card" style="margin-top:1rem">
          <div class="section-title">Pending changes ({{ scenario.pendingDeltas.length }})</div>
          <div v-if="!scenario.pendingDeltas.length" class="empty" style="padding:1rem 0">
            No changes yet.
          </div>
          <table v-else>
            <thead>
              <tr><th>Entity</th><th>Field</th><th>New value</th><th></th></tr>
            </thead>
            <tbody>
              <tr v-for="(d, i) in scenario.pendingDeltas" :key="i">
                <td>
                  <span class="tag tag-blue" style="font-size:.7rem;margin-right:4px">{{ d.entityType }}</span>
                  {{ lookupEntityName(d.entityType, d.entityId) }}
                </td>
                <td>{{ lookupFieldLabel(d.entityType, d.field) }}</td>
                <td>{{ d.newValue }}</td>
                <td>
                  <button class="btn btn-outline" style="padding:2px 8px;font-size:.75rem"
                    @click="removeDelta(i)">✕</button>
                </td>
              </tr>
            </tbody>
          </table>

          <div class="delta-actions">
            <button class="btn btn-primary"
              :disabled="!scenario.pendingDeltas.length || scenario.previewLoading"
              @click="scenario.preview()">
              {{ scenario.previewLoading ? 'Computing…' : 'Preview impact' }}
            </button>
            <button class="btn btn-outline" :disabled="!scenario.pendingDeltas.length"
              @click="scenario.clearDeltas()">
              Clear all
            </button>
          </div>
        </div>

        <!-- Commit form -->
        <div v-if="scenario.impact" class="card" style="margin-top:1rem">
          <div class="section-title">Commit scenario</div>
          <label class="form-label">Reason (required)
            <textarea v-model="commitForm.reason" rows="2"
              placeholder="Why are these changes being made?" />
          </label>
          <label class="form-label" style="margin-top:.5rem">Source
            <select v-model="commitForm.source">
              <option value="CHANGE_REQUEST">Change request</option>
              <option value="TEAM_FEEDBACK">Team feedback</option>
            </select>
          </label>
          <div class="delta-actions" style="margin-top:.75rem">
            <button class="btn btn-primary"
              :disabled="!commitForm.reason.trim() || committing" @click="doCommit">
              {{ committing ? 'Committing…' : 'Commit changes' }}
            </button>
          </div>
          <div v-if="commitError" class="error-msg">{{ commitError }}</div>
        </div>
      </div>

      <!-- Right: impact preview -->
      <div class="right-panel">
        <div class="card">
          <div class="section-title">Impact preview</div>

          <div v-if="!scenario.impact && !scenario.previewLoading" class="empty" style="padding:2rem 0">
            Add changes and click "Preview impact".
          </div>

          <div v-if="scenario.previewLoading" class="loading" style="padding:2rem 0">Computing…</div>

          <template v-if="scenario.impact">
            <div class="impact-section">
              <div class="impact-heading new-flags">
                🔺 New flags ({{ scenario.impact.newFlags.length }})
              </div>
              <div v-if="!scenario.impact.newFlags.length" class="empty" style="padding:.5rem 0">None.</div>
              <div v-for="flag in scenario.impact.newFlags" :key="flag.type + flag.entityId" class="flag-item">
                <span :class="['tag', flag.severity === 'RED' ? 'tag-red' : 'tag-amber']">{{ flag.severity }}</span>
                <span class="flag-msg">{{ flag.message }}</span>
              </div>
            </div>

            <div class="impact-section" style="margin-top:1rem">
              <div class="impact-heading resolved-flags">
                ✅ Resolved flags ({{ scenario.impact.resolvedFlags.length }})
              </div>
              <div v-if="!scenario.impact.resolvedFlags.length" class="empty" style="padding:.5rem 0">None.</div>
              <div v-for="flag in scenario.impact.resolvedFlags" :key="flag.type + flag.entityId" class="flag-item">
                <span class="tag tag-green">RESOLVED</span>
                <span class="flag-msg">{{ flag.message }}</span>
              </div>
            </div>

            <div class="impact-section" style="margin-top:1rem">
              <div class="impact-heading">Capacity deltas</div>
              <div v-if="!scenario.impact.capacityDeltas.length" class="empty" style="padding:.5rem 0">
                No capacity changes.
              </div>
              <table v-else style="margin-top:.5rem">
                <thead>
                  <tr>
                    <th>Team</th><th>Period start</th>
                    <th>Baseline (pd)</th><th>Scenario (pd)</th><th>Delta</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(d, i) in scenario.impact.capacityDeltas" :key="i">
                    <td>{{ d.teamName }}</td>
                    <td>{{ d.periodStart }}</td>
                    <td>{{ d.baselineLoadPd }}</td>
                    <td>{{ d.scenarioLoadPd }}</td>
                    <td>
                      <span :class="['tag', d.deltaLoadPd > 0 ? 'tag-amber' : 'tag-green']">
                        {{ d.deltaLoadPd > 0 ? '+' : '' }}{{ d.deltaLoadPd }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useScenarioStore, usePlanningStore } from '@/stores/planning'
import { peopleApi } from '@/api/client'
import type { Person } from '@/types'

const scenario = useScenarioStore()
const planning = usePlanningStore()

onMounted(async () => {
  if (!planning.teams.length) await planning.loadAll()
})

// ── Field definitions ─────────────────────────────────────────────────────────

interface FieldDef {
  field: string
  label: string
  type: 'number' | 'date' | 'enum'
  options?: string[]
  min?: number
  max?: number
  step?: number
  readKey: string
}

const FIELD_DEFS: Record<string, FieldDef[]> = {
  TEAM: [
    { field: 'overheadFactor', label: 'Overhead factor (0–1)', type: 'number', min: 0, max: 1, step: 0.05, readKey: 'overheadFactor' },
    { field: 'supportFactor',  label: 'Support factor (0–1)',  type: 'number', min: 0, max: 1, step: 0.05, readKey: 'supportFactor'  },
  ],
  EPIC: [
    { field: 'estimate',  label: 'Estimate (pd)', type: 'number', min: 0, step: 1, readKey: 'estimatePd' },
    { field: 'status',    label: 'Status',        type: 'enum', options: ['COMMITTED', 'AT_RISK', 'DEFERRED', 'DONE'], readKey: 'status' },
    { field: 'priority',  label: 'Priority',      type: 'enum', options: ['MUST', 'SHOULD', 'COULD', 'WONT'],         readKey: 'priority' },
    { field: 'startDate', label: 'Start date',    type: 'date', readKey: 'startDate' },
    { field: 'dueDate',   label: 'Due date',      type: 'date', readKey: 'dueDate'   },
  ],
  INITIATIVE: [
    { field: 'topDownEstimate',    label: 'Top-down estimate (pd)',   type: 'number', min: 0, step: 1, readKey: 'topDownEstimatePd'  },
    { field: 'status',             label: 'Status',                   type: 'enum', options: ['PROPOSED', 'COMMITTED', 'AT_RISK', 'DELIVERED', 'CANCELLED'], readKey: 'status' },
    { field: 'targetDeliveryDate', label: 'Target delivery date',     type: 'date', readKey: 'targetDeliveryDate' },
  ],
  PERSON: [
    { field: 'baseAvailability', label: 'Base availability (0–1)', type: 'number', min: 0, max: 1, step: 0.05, readKey: 'baseAvailability' },
  ],
}

// ── Form state ────────────────────────────────────────────────────────────────

type EntityType = 'TEAM' | 'EPIC' | 'INITIATIVE' | 'PERSON'

const form = ref<{ entityType: EntityType; entityId: string; field: string; newValue: string | number }>({
  entityType: 'TEAM',
  entityId: '',
  field: '',
  newValue: '',
})

const people         = ref<Person[]>([])
const loadingPeople  = ref(false)

// ── Derived ───────────────────────────────────────────────────────────────────

const entityTypeLabel = computed(() =>
  ({ TEAM: 'Team', EPIC: 'Epic', INITIATIVE: 'Initiative', PERSON: 'Person' }[form.value.entityType])
)

const entityOptions = computed((): { id: string; name: string }[] => {
  switch (form.value.entityType) {
    case 'TEAM':       return planning.teams.map(t => ({ id: t.id, name: t.name }))
    case 'EPIC':       return planning.epics.map(e => ({ id: e.id, name: e.name }))
    case 'INITIATIVE': return planning.initiatives.map(i => ({ id: i.id, name: i.name }))
    case 'PERSON':     return people.value.map(p => ({ id: p.id, name: p.name }))
  }
})

const fieldOptions = computed(() => FIELD_DEFS[form.value.entityType] ?? [])

const selectedFieldDef = computed(() =>
  fieldOptions.value.find(f => f.field === form.value.field) ?? null
)

const selectedEntity = computed(() => {
  if (!form.value.entityId) return null
  switch (form.value.entityType) {
    case 'TEAM':       return planning.teams.find(t => t.id === form.value.entityId) ?? null
    case 'EPIC':       return planning.epics.find(e => e.id === form.value.entityId) ?? null
    case 'INITIATIVE': return planning.initiatives.find(i => i.id === form.value.entityId) ?? null
    case 'PERSON':     return people.value.find(p => p.id === form.value.entityId) ?? null
  }
})

const currentValue = computed((): string | number | null => {
  if (!selectedEntity.value || !selectedFieldDef.value) return null
  return (selectedEntity.value as Record<string, unknown>)[selectedFieldDef.value.readKey] as string | number ?? null
})

const canAddDelta = computed(() => {
  if (!form.value.entityId || !form.value.field) return false
  const v = form.value.newValue
  if (v === '' || v === null) return false
  if (typeof v === 'number' && isNaN(v)) return false
  return true
})

// ── Handlers ──────────────────────────────────────────────────────────────────

async function onTypeChange() {
  form.value.entityId = ''
  form.value.field    = ''
  form.value.newValue = ''
  if (form.value.entityType === 'PERSON' && !people.value.length) {
    loadingPeople.value = true
    try { people.value = (await peopleApi.list()).data }
    finally { loadingPeople.value = false }
  }
}

function onEntityChange() {
  form.value.field    = ''
  form.value.newValue = ''
}

function onFieldChange() {
  form.value.newValue = currentValue.value !== null ? currentValue.value : ''
}

function addDelta() {
  if (!canAddDelta.value) return
  scenario.addDelta({
    entityType: form.value.entityType,
    entityId:   form.value.entityId,
    field:      form.value.field,
    newValue:   form.value.newValue,
  })
  form.value.field    = ''
  form.value.newValue = ''
}

function removeDelta(index: number) {
  scenario.pendingDeltas.splice(index, 1)
}

// ── Lookup helpers for pending table ─────────────────────────────────────────

function lookupEntityName(entityType: string, entityId: string): string {
  switch (entityType) {
    case 'TEAM':       return planning.teams.find(t => t.id === entityId)?.name        ?? entityId.slice(0, 8)
    case 'EPIC':       return planning.epics.find(e => e.id === entityId)?.name        ?? entityId.slice(0, 8)
    case 'INITIATIVE': return planning.initiatives.find(i => i.id === entityId)?.name  ?? entityId.slice(0, 8)
    case 'PERSON':     return people.value.find(p => p.id === entityId)?.name          ?? entityId.slice(0, 8)
    default:           return entityId.slice(0, 8)
  }
}

function lookupFieldLabel(entityType: string, field: string): string {
  return FIELD_DEFS[entityType]?.find(f => f.field === field)?.label ?? field
}

// ── Commit ────────────────────────────────────────────────────────────────────

const commitForm  = ref({ reason: '', source: 'CHANGE_REQUEST' as 'CHANGE_REQUEST' | 'TEAM_FEEDBACK' })
const committing  = ref(false)
const commitError = ref<string | null>(null)

async function doCommit() {
  committing.value  = true
  commitError.value = null
  try {
    await scenario.commit(commitForm.value.reason, commitForm.value.source)
    await planning.loadAll()
    commitForm.value.reason = ''
  } catch (e: any) {
    commitError.value = e.message
  } finally {
    committing.value = false
  }
}
</script>

<style scoped>
.layout { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; align-items: start; }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: .5rem; }
.form-label { display: flex; flex-direction: column; gap: 4px; font-size: .8rem; color: var(--c-muted); }
.form-label select, .form-label input, .form-label textarea {
  border: 1px solid var(--c-border); border-radius: 4px; padding: 5px 8px;
  font-size: .875rem; background: #fff; font-family: inherit; color: var(--c-text);
}
.form-label select:disabled { opacity: .5; cursor: not-allowed; }

.current-row { display: flex; align-items: center; gap: .5rem; margin-top: .625rem;
  padding: .4rem .6rem; background: #f1f5f9; border-radius: 4px; font-size: .8rem; }
.current-label { color: var(--c-muted); white-space: nowrap; }
.current-val { font-family: monospace; font-size: .85rem; color: var(--c-text); background: none; }

.delta-actions { display: flex; gap: .5rem; margin-top: .75rem; flex-wrap: wrap; }
.error-msg { margin-top: .5rem; color: var(--c-red); font-size: .875rem; }

.impact-section {}
.impact-heading { font-weight: 600; font-size: .875rem; margin-bottom: .5rem; }
.new-flags { color: #991b1b; }
.resolved-flags { color: #065f46; }
.flag-item { display: flex; align-items: flex-start; gap: .5rem; margin-bottom: .25rem; font-size: .875rem; }
.flag-msg { flex: 1; }
</style>
