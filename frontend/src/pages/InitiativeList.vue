<template>
  <div>
    <h1 class="section-title">Initiatives</h1>

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
                v-for="flag in ini.flags"
                :key="flag.type"
                :class="['tag', flag.severity === 'RED' ? 'tag-red' : 'tag-amber']"
                style="margin-right: 4px"
              >{{ shortFlag(flag.type) }}</span>
              <span v-if="!ini.flags.length" class="tag tag-gray">—</span>
            </td>
            <td>{{ ini.targetDeliveryDate }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { usePlanningStore } from '@/stores/planning'
import type { InitiativeStatus, FlagType } from '@/types'

const planning = usePlanningStore()

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
