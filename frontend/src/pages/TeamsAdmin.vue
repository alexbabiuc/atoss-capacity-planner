<template>
  <div>
    <div class="page-header-row">
      <h1 class="section-title" style="margin:0">Teams</h1>
      <button class="btn btn-primary" @click="openCreate">+ New Team</button>
    </div>

    <div v-if="loading" class="loading">Loading…</div>
    <div v-else-if="!teams.length" class="empty">No teams yet.</div>

    <div v-else class="card" style="overflow-x:auto">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Members</th>
            <th>Overhead</th>
            <th>Support</th>
            <th>Focus factor</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in teams" :key="t.id">
            <td>
              <RouterLink :to="`/admin/people?teamId=${t.id}`">{{ t.name }}</RouterLink>
            </td>
            <td>{{ t.memberCount }}</td>
            <td>{{ pct(t.overheadFactor) }}%</td>
            <td>{{ pct(t.supportFactor) }}%</td>
            <td>
              <span :class="['tag', focusClass(t)]">
                {{ pct(1 - t.overheadFactor - t.supportFactor) }}%
              </span>
            </td>
            <td style="text-align:right">
              <button class="btn btn-outline btn-sm" @click="openEdit(t)">Edit</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create / Edit modal -->
    <div v-if="modal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-card">
        <h2 class="modal-title">{{ form.id ? 'Edit Team' : 'New Team' }}</h2>
        <form @submit.prevent="save">
          <div class="field">
            <label>Name *</label>
            <input v-model="form.name" required placeholder="e.g. Payments Backend" />
          </div>
          <div class="field">
            <label>Overhead factor <span class="hint">(ceremonies, 1:1s)</span></label>
            <input v-model.number="form.overheadFactor" type="number" min="0" max="0.99" step="0.01" required />
          </div>
          <div class="field">
            <label>Support factor <span class="hint">(on-call, interrupts)</span></label>
            <input v-model.number="form.supportFactor" type="number" min="0" max="0.99" step="0.01" required />
          </div>
          <p class="hint" style="margin-bottom:1rem">
            Effective focus: <strong>{{ focusPct }}%</strong>
            &nbsp;(= 1 − overhead − support)
          </p>
          <div v-if="err" class="form-error">{{ err }}</div>
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
import { ref, computed, onMounted } from 'vue'
import { teamsApi } from '@/api/client'
import type { TeamSummary } from '@/types'

const today   = new Date().toISOString().slice(0, 10)
const horizon = new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10)

const teams   = ref<TeamSummary[]>([])
const loading = ref(true)
const modal   = ref(false)
const saving  = ref(false)
const err     = ref('')

const blank = () => ({ id: '', name: '', overheadFactor: 0.15, supportFactor: 0.10 })
const form  = ref(blank())

const focusPct = computed(() =>
  Math.max(0, Math.round((1 - form.value.overheadFactor - form.value.supportFactor) * 100))
)

onMounted(load)

async function load() {
  loading.value = true
  try {
    teams.value = (await teamsApi.list('MONTH', today, horizon)).data
  } finally {
    loading.value = false
  }
}

function pct(v: number) { return Math.round(v * 100) }

function focusClass(t: TeamSummary) {
  const f = 1 - t.overheadFactor - t.supportFactor
  return f >= 0.65 ? 'tag-green' : f >= 0.5 ? 'tag-amber' : 'tag-red'
}

function openCreate() { form.value = blank(); err.value = ''; modal.value = true }

function openEdit(t: TeamSummary) {
  form.value = { id: t.id, name: t.name, overheadFactor: t.overheadFactor, supportFactor: t.supportFactor }
  err.value = ''
  modal.value = true
}

function closeModal() { modal.value = false }

async function save() {
  saving.value = true
  err.value = ''
  try {
    const payload = { name: form.value.name, overheadFactor: form.value.overheadFactor, supportFactor: form.value.supportFactor }
    if (form.value.id) {
      await teamsApi.update(form.value.id, payload)
    } else {
      await teamsApi.create(payload)
    }
    closeModal()
    await load()
  } catch (e: any) {
    err.value = e?.response?.data?.message ?? 'Save failed. Check the values and try again.'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page-header-row { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; }
.btn-sm { font-size:.8rem; padding:3px 10px; }
</style>
