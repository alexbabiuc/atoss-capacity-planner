<template>
  <div>
    <!-- Header -->
    <div class="page-header-row">
      <div style="display:flex;align-items:center;gap:1rem">
        <h1 class="section-title" style="margin:0">People</h1>
        <select v-model="teamFilter" class="team-filter" @change="filterChanged">
          <option value="">All teams</option>
          <option v-for="t in allTeams" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
      </div>
      <button class="btn btn-primary" @click="openCreate">+ New Person</button>
    </div>

    <div v-if="loading" class="loading">Loading…</div>
    <div v-else-if="!people.length" class="empty">No people found.</div>

    <div v-else class="card" style="overflow-x:auto">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Team</th>
            <th>Availability</th>
            <th>Skills</th>
            <th>Overrides</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in people" :key="p.id">
            <td>{{ p.name }}</td>
            <td>{{ teamName(p.teamId) }}</td>
            <td>{{ pct(p.baseAvailability) }}%</td>
            <td>
              <span v-for="ps in p.skills" :key="ps.skill.id" class="tag tag-blue" style="margin-right:3px;font-size:.7rem">
                {{ ps.skill.name }}
                <span class="tag tag-gray" style="margin-left:2px;font-size:.65rem">{{ ps.proficiency[0] }}</span>
              </span>
              <span v-if="!p.skills.length" class="hint">—</span>
            </td>
            <td>
              <span v-if="p.availabilityOverrides.length" class="tag tag-amber">
                {{ p.availabilityOverrides.length }} override{{ p.availabilityOverrides.length > 1 ? 's' : '' }}
              </span>
              <span v-else class="hint">—</span>
            </td>
            <td style="text-align:right;white-space:nowrap">
              <button class="btn btn-outline btn-sm" @click="openEdit(p)" style="margin-right:.25rem">Edit</button>
              <button class="btn btn-outline btn-sm" @click="openOverrides(p)">Overrides</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Person create/edit modal -->
    <div v-if="personModal" class="modal-overlay" @click.self="closePersonModal">
      <div class="modal-card" style="max-width:560px">
        <h2 class="modal-title">{{ form.id ? 'Edit Person' : 'New Person' }}</h2>
        <form @submit.prevent="savePerson">
          <div class="field">
            <label>Name *</label>
            <input v-model="form.name" required placeholder="e.g. Maria Schmidt" />
          </div>
          <div class="field">
            <label>Team *</label>
            <select v-model="form.teamId" required>
              <option value="" disabled>Select team…</option>
              <option v-for="t in allTeams" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </div>
          <div class="field">
            <label>Base availability <span class="hint">(FTE fraction, 0–1)</span></label>
            <input v-model.number="form.baseAvailability" type="number" min="0" max="1" step="0.05" required />
          </div>

          <!-- Skills -->
          <div style="margin-bottom:.875rem">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:.5rem">
              <label style="font-size:.8rem;font-weight:500;color:var(--c-muted)">Skills</label>
              <button type="button" class="btn btn-outline btn-sm" @click="addSkillRow">+ Add skill</button>
            </div>
            <div v-if="!form.skills.length" class="hint" style="padding:.5rem 0">No skills assigned.</div>
            <div v-for="(row, i) in form.skills" :key="i" class="skill-row">
              <select v-model="row.skillId" style="flex:1">
                <option value="" disabled>Select skill…</option>
                <option v-for="sk in knownSkills" :key="sk.id" :value="sk.id">
                  {{ sk.name }}{{ sk.category ? ` (${sk.category})` : '' }}
                </option>
              </select>
              <select v-model="row.proficiency">
                <option>FAMILIAR</option>
                <option>PROFICIENT</option>
                <option>EXPERT</option>
              </select>
              <button type="button" class="btn btn-danger btn-sm" @click="removeSkillRow(i)">×</button>
            </div>
            <p v-if="!knownSkills.length" class="hint" style="margin-top:.25rem">
              No skills exist in the system yet. Create people with skills first via the Scenario Builder.
            </p>
          </div>

          <div v-if="personErr" class="form-error">{{ personErr }}</div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="closePersonModal">Cancel</button>
            <button type="submit" class="btn btn-primary" :disabled="personSaving">
              {{ personSaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Availability overrides modal -->
    <div v-if="overridesModal" class="modal-overlay" @click.self="closeOverridesModal">
      <div class="modal-card" style="max-width:600px">
        <h2 class="modal-title">Availability Overrides — {{ selectedPerson?.name }}</h2>
        <p class="hint" style="margin-bottom:1rem">
          Overrides model PTO, parental leave, part-time periods, or departures.
          A factor of 0 means the person is unavailable.
        </p>

        <div v-if="overrideLoading" class="loading" style="padding:1rem 0">Loading…</div>
        <template v-else>
          <!-- Existing overrides -->
          <div v-if="!overrideList.length" class="empty" style="padding:.75rem 0">No overrides.</div>
          <table v-else style="margin-bottom:1rem">
            <thead>
              <tr>
                <th>Start</th>
                <th>End</th>
                <th>Factor</th>
                <th>Reason</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(ov, i) in overrideList" :key="i">
                <td>{{ ov.startDate }}</td>
                <td>{{ ov.endDate ?? '—' }}</td>
                <td>{{ pct(ov.factor) }}%</td>
                <td>{{ ov.reason }}</td>
                <td>
                  <button class="btn btn-danger btn-sm" :disabled="removingIdx === i" @click="removeOverride(i)">
                    {{ removingIdx === i ? '…' : 'Remove' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>

          <!-- Add override form -->
          <div class="card" style="background:#f8fafc">
            <div style="font-size:.8rem;font-weight:600;margin-bottom:.75rem">Add override</div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
              <div class="field" style="margin:0">
                <label>Start date *</label>
                <input v-model="newOverride.startDate" type="date" required />
              </div>
              <div class="field" style="margin:0">
                <label>End date <span class="hint">(blank = permanent)</span></label>
                <input v-model="newOverride.endDate" type="date" />
              </div>
              <div class="field" style="margin:0">
                <label>Factor <span class="hint">(0 = absent, 0.5 = half)</span></label>
                <input v-model.number="newOverride.factor" type="number" min="0" max="1" step="0.05" required />
              </div>
              <div class="field" style="margin:0">
                <label>Reason *</label>
                <input v-model="newOverride.reason" placeholder="e.g. Parental leave" required />
              </div>
            </div>
            <div v-if="overrideErr" class="form-error" style="margin-top:.75rem">{{ overrideErr }}</div>
            <div class="form-actions" style="margin-top:.75rem">
              <button class="btn btn-primary" :disabled="addingOverride" @click="addOverride">
                {{ addingOverride ? 'Adding…' : 'Add Override' }}
              </button>
            </div>
          </div>
        </template>

        <div class="form-actions" style="margin-top:1rem">
          <button class="btn btn-outline" @click="closeOverridesModal">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { peopleApi, teamsApi } from '@/api/client'
import type { Person, TeamSummary, Skill, AvailabilityOverride } from '@/types'

const route = useRoute()

const today   = new Date().toISOString().slice(0, 10)
const horizon = new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10)

// State
const allTeams   = ref<TeamSummary[]>([])
const people     = ref<Person[]>([])
const loading    = ref(true)
const teamFilter = ref((route.query.teamId as string) ?? '')

// Derived: all skills known in the system
const knownSkills = computed<Skill[]>(() => {
  const map = new Map<string, Skill>()
  people.value.forEach(p => p.skills.forEach(ps => map.set(ps.skill.id, ps.skill)))
  return [...map.values()].sort((a, b) => a.name.localeCompare(b.name))
})

// Person modal
const personModal  = ref(false)
const personSaving = ref(false)
const personErr    = ref('')

interface SkillRow { skillId: string; proficiency: string }
const blankForm = () => ({
  id: '', name: '', teamId: '', baseAvailability: 1.0,
  skills: [] as SkillRow[],
})
const form = ref(blankForm())

// Overrides modal
const overridesModal    = ref(false)
const selectedPerson    = ref<Person | null>(null)
const overrideList      = ref<AvailabilityOverride[]>([])
const overrideLoading   = ref(false)
const addingOverride    = ref(false)
const removingIdx       = ref<number | null>(null)
const overrideErr       = ref('')
const newOverride       = ref({ startDate: '', endDate: '', factor: 0, reason: '' })

onMounted(async () => {
  await Promise.all([loadTeams(), loadPeople()])
  loading.value = false
})

async function loadTeams() {
  allTeams.value = (await teamsApi.list('MONTH', today, horizon)).data
}

async function loadPeople() {
  const params = teamFilter.value ? teamFilter.value : undefined
  people.value = (await peopleApi.list(params)).data
}

async function filterChanged() {
  loading.value = true
  try { await loadPeople() } finally { loading.value = false }
}

function pct(v: number) { return Math.round(v * 100) }
function teamName(id: string) { return allTeams.value.find(t => t.id === id)?.name ?? id.slice(0, 8) }

// ── Person modal ─────────────────────────────────────────────────────────────

function openCreate() {
  form.value = blankForm()
  personErr.value = ''
  personModal.value = true
}

function openEdit(p: Person) {
  form.value = {
    id: p.id,
    name: p.name,
    teamId: p.teamId,
    baseAvailability: p.baseAvailability,
    skills: p.skills.map(ps => ({ skillId: ps.skill.id, proficiency: ps.proficiency })),
  }
  personErr.value = ''
  personModal.value = true
}

function closePersonModal() { personModal.value = false }

function addSkillRow() {
  form.value.skills.push({ skillId: knownSkills.value[0]?.id ?? '', proficiency: 'PROFICIENT' })
}

function removeSkillRow(i: number) { form.value.skills.splice(i, 1) }

async function savePerson() {
  personSaving.value = true
  personErr.value = ''
  try {
    const payload = {
      name: form.value.name,
      teamId: form.value.teamId,
      baseAvailability: form.value.baseAvailability,
      skills: form.value.skills
        .filter(r => r.skillId)
        .map(r => ({ skillId: r.skillId, proficiency: r.proficiency })),
    }

    if (form.value.id) {
      await peopleApi.update(form.value.id, payload)
    } else {
      await peopleApi.create(payload)
    }
    closePersonModal()
    loading.value = true
    await loadPeople()
    loading.value = false
  } catch (e: any) {
    personErr.value = e?.response?.data?.message ?? 'Save failed.'
  } finally {
    personSaving.value = false
  }
}

// ── Overrides modal ──────────────────────────────────────────────────────────

async function openOverrides(p: Person) {
  selectedPerson.value = p
  overrideErr.value = ''
  newOverride.value = { startDate: '', endDate: '', factor: 0, reason: '' }
  overridesModal.value = true
  overrideLoading.value = true
  try {
    const { data } = await peopleApi.get(p.id)
    overrideList.value = data.availabilityOverrides
  } finally {
    overrideLoading.value = false
  }
}

function closeOverridesModal() { overridesModal.value = false; selectedPerson.value = null }

async function addOverride() {
  if (!selectedPerson.value) return
  if (!newOverride.value.startDate || !newOverride.value.reason) {
    overrideErr.value = 'Start date and reason are required.'
    return
  }
  addingOverride.value = true
  overrideErr.value = ''
  try {
    const payload: AvailabilityOverride = {
      startDate: newOverride.value.startDate,
      endDate: newOverride.value.endDate || undefined,
      factor: newOverride.value.factor,
      reason: newOverride.value.reason,
    }
    const { data } = await peopleApi.addOverride(selectedPerson.value.id, payload)
    overrideList.value = data.availabilityOverrides
    newOverride.value = { startDate: '', endDate: '', factor: 0, reason: '' }
  } catch (e: any) {
    overrideErr.value = e?.response?.data?.message ?? 'Failed to add override.'
  } finally {
    addingOverride.value = false
  }
}

async function removeOverride(index: number) {
  if (!selectedPerson.value) return
  removingIdx.value = index
  try {
    const { data } = await peopleApi.removeOverride(selectedPerson.value.id, index)
    overrideList.value = data.availabilityOverrides
  } finally {
    removingIdx.value = null
  }
}
</script>

<style scoped>
.page-header-row { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; }
.team-filter { border:1px solid var(--c-border); border-radius:4px; padding:4px 8px; font-size:.875rem; }
.btn-sm { font-size:.8rem; padding:3px 10px; }
.skill-row { display:flex; gap:.5rem; align-items:center; margin-bottom:.375rem; }
.skill-row select { border:1px solid var(--c-border); border-radius:4px; padding:5px 8px; font-size:.875rem; }
</style>
