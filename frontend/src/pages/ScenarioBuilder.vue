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
              <select v-model="form.entityType">
                <option value="EPIC">Epic</option>
                <option value="PERSON">Person</option>
                <option value="TEAM">Team</option>
                <option value="INITIATIVE">Initiative</option>
              </select>
            </label>
            <label class="form-label">Entity ID
              <input v-model="form.entityId" placeholder="UUID" />
            </label>
            <label class="form-label">Field
              <input v-model="form.field" placeholder="e.g. estimatePd" />
            </label>
            <label class="form-label">New value
              <input v-model="form.newValue" placeholder="e.g. 42" />
            </label>
          </div>
          <button class="btn btn-primary" style="margin-top:.75rem" @click="addDelta">Add delta</button>
        </div>

        <!-- Pending deltas -->
        <div class="card" style="margin-top:1rem">
          <div class="section-title">Pending changes ({{ scenario.pendingDeltas.length }})</div>
          <div v-if="!scenario.pendingDeltas.length" class="empty" style="padding:1rem 0">
            No changes yet.
          </div>
          <table v-else>
            <thead><tr><th>Entity</th><th>Field</th><th>New value</th><th></th></tr></thead>
            <tbody>
              <tr v-for="(d, i) in scenario.pendingDeltas" :key="i">
                <td><span class="tag tag-blue">{{ d.entityType }}</span> {{ d.entityId.slice(0, 8) }}…</td>
                <td>{{ d.field }}</td>
                <td>{{ d.newValue }}</td>
                <td><button class="btn btn-outline" style="padding:2px 8px;font-size:.75rem" @click="removeDelta(i)">✕</button></td>
              </tr>
            </tbody>
          </table>

          <div class="delta-actions">
            <button class="btn btn-primary" :disabled="!scenario.pendingDeltas.length || scenario.previewLoading" @click="scenario.preview()">
              {{ scenario.previewLoading ? 'Computing…' : 'Preview impact' }}
            </button>
            <button class="btn btn-outline" :disabled="!scenario.pendingDeltas.length" @click="scenario.clearDeltas()">
              Clear all
            </button>
          </div>
        </div>

        <!-- Commit form -->
        <div v-if="scenario.impact" class="card" style="margin-top:1rem">
          <div class="section-title">Commit scenario</div>
          <label class="form-label">Reason (required)
            <textarea v-model="commitForm.reason" rows="2" placeholder="Why are these changes being made?" />
          </label>
          <label class="form-label" style="margin-top:.5rem">Source
            <select v-model="commitForm.source">
              <option value="CHANGE_REQUEST">Change request</option>
              <option value="TEAM_FEEDBACK">Team feedback</option>
            </select>
          </label>
          <div class="delta-actions" style="margin-top:.75rem">
            <button class="btn btn-primary" :disabled="!commitForm.reason.trim() || committing" @click="doCommit">
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
            <!-- New flags -->
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

            <!-- Resolved flags -->
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

            <!-- Capacity deltas -->
            <div class="impact-section" style="margin-top:1rem">
              <div class="impact-heading">Capacity deltas</div>
              <div v-if="!scenario.impact.capacityDeltas.length" class="empty" style="padding:.5rem 0">No capacity changes.</div>
              <table v-else style="margin-top:.5rem">
                <thead>
                  <tr><th>Team</th><th>Period start</th><th>Baseline (pd)</th><th>Scenario (pd)</th><th>Delta</th></tr>
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
import { ref } from 'vue'
import { useScenarioStore } from '@/stores/planning'
import { usePlanningStore } from '@/stores/planning'

const scenario = useScenarioStore()
const planning = usePlanningStore()

const form = ref({ entityType: 'EPIC', entityId: '', field: '', newValue: '' })
const commitForm = ref({ reason: '', source: 'CHANGE_REQUEST' as 'CHANGE_REQUEST' | 'TEAM_FEEDBACK' })
const committing = ref(false)
const commitError = ref<string | null>(null)

function addDelta() {
  if (!form.value.entityId || !form.value.field || form.value.newValue === '') return
  scenario.addDelta({
    entityType: form.value.entityType,
    entityId: form.value.entityId,
    field: form.value.field,
    newValue: form.value.newValue,
  })
  form.value.entityId = ''
  form.value.field = ''
  form.value.newValue = ''
}

function removeDelta(index: number) {
  scenario.pendingDeltas.splice(index, 1)
}

async function doCommit() {
  committing.value = true
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
  font-size: .875rem; background: #fff; font-family: inherit;
}

.delta-actions { display: flex; gap: .5rem; margin-top: .75rem; flex-wrap: wrap; }
.error-msg { margin-top: .5rem; color: var(--c-red); font-size: .875rem; }

.impact-section {}
.impact-heading { font-weight: 600; font-size: .875rem; margin-bottom: .5rem; }
.new-flags { color: #991b1b; }
.resolved-flags { color: #065f46; }
.flag-item { display: flex; align-items: flex-start; gap: .5rem; margin-bottom: .25rem; font-size: .875rem; }
.flag-msg { flex: 1; }
</style>
