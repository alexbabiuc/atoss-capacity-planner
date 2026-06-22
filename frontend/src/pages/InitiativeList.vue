<template>
  <div>
    <div class="page-header-row">
      <h1 class="section-title" style="margin:0">Initiatives</h1>
      <button class="btn btn-primary" @click="openCreate">+ New Initiative</button>
    </div>

    <div v-if="planning.loading" class="loading">Loading…</div>
    <div v-else-if="!planning.initiatives.length" class="empty">No initiatives found.</div>

    <div v-else class="card" style="overflow-x:auto">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Status</th>
            <th>Top-down (pd)</th>
            <th>Bottom-up (pd)</th>
            <th>Gap (pd)</th>
            <th>Flags</th>
            <th>Target delivery</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ini in planning.initiatives" :key="ini.id">
            <td><RouterLink :to="`/initiatives/${ini.id}`">{{ ini.name }}</RouterLink></td>
            <td><span :class="['tag', statusClass(ini.status)]">{{ ini.status }}</span></td>
            <td>{{ ini.topDownEstimatePd }}</td>
            <td>{{ ini.bottomUpEstimatePd }}</td>
            <td>
              <span :class="['tag', ini.decompositionGapPd > 0 ? 'tag-amber' : 'tag-green']">
                {{ ini.decompositionGapPd > 0 ? '+' : '' }}{{ ini.decompositionGapPd }}
              </span>
            </td>
            <td>
              <span
                v-for="flag in ini.flags" :key="flag.type"
                :class="['tag', flag.severity === 'RED' ? 'tag-red' : 'tag-amber']"
                style="margin-right:4px"
              >{{ shortFlag(flag.type) }}</span>
              <span v-if="!ini.flags.length" class="tag tag-gray">—</span>
            </td>
            <td>{{ ini.targetDeliveryDate }}</td>
            <td style="text-align:right">
              <button class="btn btn-outline btn-sm" @click="openEdit(ini)">Edit</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create / Edit modal -->
    <div v-if="modal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-card">
        <h2 class="modal-title">{{ form.id ? 'Edit Initiative' : 'New Initiative' }}</h2>
        <form @submit.prevent="save">
          <div class="field">
            <label>Name *</label>
            <input v-model="form.name" required placeholder="e.g. Open Banking Phase 2" />
          </div>
          <div class="field">
            <label>Description</label>
            <textarea v-model="form.description" placeholder="Optional context…" />
          </div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
            <div class="field" style="margin:0">
              <label>Top-down estimate (pd) *</label>
              <input v-model.number="form.topDownEstimate" type="number" min="0" step="1" required />
            </div>
            <div class="field" style="margin:0">
              <label>Priority <span class="hint">(lower = higher)</span></label>
              <input v-model.number="form.priority" type="number" min="1" step="1" />
            </div>
            <div class="field" style="margin:0">
              <label>Start date</label>
              <input v-model="form.startDate" type="date" />
            </div>
            <div class="field" style="margin:0">
              <label>Target delivery *</label>
              <input v-model="form.targetDeliveryDate" type="date" required />
            </div>
          </div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem;margin-top:.75rem">
            <div class="field" style="margin:0">
              <label>Status</label>
              <select v-model="form.status">
                <option>PROPOSED</option>
                <option>COMMITTED</option>
                <option>AT_RISK</option>
                <option>DELIVERED</option>
                <option>CANCELLED</option>
              </select>
            </div>
            <div class="field" style="margin:0">
              <label>Owner name</label>
              <input v-model="form.ownerName" placeholder="e.g. Jan Müller" />
            </div>
          </div>
          <div v-if="err" class="form-error" style="margin-top:.875rem">{{ err }}</div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="closeModal">Cancel</button>
            <button type="submit" class="btn btn-primary" :disabled="saving">
              {{ saving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { usePlanningStore } from '@/stores/planning'
import { initiativesApi } from '@/api/client'
import type { InitiativeStatus, FlagType, InitiativeSummary } from '@/types'

const planning = usePlanningStore()

// Modal state
const modal  = ref(false)
const saving = ref(false)
const err    = ref('')

const blankForm = () => ({
  id: '', name: '', description: '', topDownEstimate: 0, priority: 10,
  startDate: '', targetDeliveryDate: '', status: 'PROPOSED' as InitiativeStatus, ownerName: '',
})
const form = ref(blankForm())

function openCreate() { form.value = blankForm(); err.value = ''; modal.value = true }

function openEdit(ini: InitiativeSummary) {
  form.value = {
    id: ini.id,
    name: ini.name,
    description: ini.description ?? '',
    topDownEstimate: ini.topDownEstimatePd,
    priority: ini.priority,
    startDate: ini.startDate ?? '',
    targetDeliveryDate: ini.targetDeliveryDate,
    status: ini.status,
    ownerName: ini.ownerName ?? '',
  }
  err.value = ''
  modal.value = true
}

function closeModal() { modal.value = false }

async function save() {
  saving.value = true
  err.value = ''
  try {
    const payload = {
      name: form.value.name,
      description: form.value.description || undefined,
      topDownEstimate: form.value.topDownEstimate,
      priority: form.value.priority,
      startDate: form.value.startDate || undefined,
      targetDeliveryDate: form.value.targetDeliveryDate,
      status: form.value.status,
      ownerName: form.value.ownerName || undefined,
    }
    if (form.value.id) {
      await initiativesApi.update(form.value.id, payload)
    } else {
      await initiativesApi.create(payload)
    }
    closeModal()
    await planning.loadAll()
  } catch (e: any) {
    err.value = e?.response?.data?.message ?? 'Save failed.'
  } finally {
    saving.value = false
  }
}

function statusClass(s: InitiativeStatus) {
  const map: Record<InitiativeStatus, string> = {
    PROPOSED: 'tag-blue', COMMITTED: 'tag-green',
    AT_RISK: 'tag-amber', DELIVERED: 'tag-gray', CANCELLED: 'tag-gray',
  }
  return map[s]
}

function shortFlag(t: FlagType): string {
  const map: Record<FlagType, string> = {
    OVER_ALLOCATION: 'OA', SKILL_SHORTFALL: 'SS',
    DECOMPOSITION_GAP: 'DG', CRITICAL_RESOURCE: 'CR',
  }
  return map[t] ?? t
}
</script>

<style scoped>
.page-header-row { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; }
.btn-sm { font-size:.8rem; padding:3px 10px; }
</style>
