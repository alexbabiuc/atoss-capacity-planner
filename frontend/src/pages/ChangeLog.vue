<template>
  <div>
    <h1 class="section-title">Change Log</h1>

    <div v-if="planning.loading" class="loading">Loading…</div>

    <div v-else-if="!planning.changeLog.length" class="empty">No changes recorded yet.</div>

    <div v-else class="card" style="overflow-x:auto">
      <table>
        <thead>
          <tr>
            <th>Timestamp</th>
            <th>Actor</th>
            <th>Source</th>
            <th>Reason</th>
            <th>Changes</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in planning.changeLog" :key="entry.id">
            <td style="white-space:nowrap">{{ formatTs(entry.timestamp) }}</td>
            <td>{{ entry.actor }}</td>
            <td>
              <span :class="['tag', entry.source === 'CHANGE_REQUEST' ? 'tag-blue' : 'tag-amber']">
                {{ sourceLabel(entry.source) }}
              </span>
            </td>
            <td>{{ entry.reason }}</td>
            <td><span class="tag tag-gray">{{ entry.changeCount }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { usePlanningStore } from '@/stores/planning'
import type { ChangeSource } from '@/types'

const planning = usePlanningStore()

function formatTs(ts: string) {
  const d = new Date(ts)
  return `${d.toLocaleDateString()} ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
}

function sourceLabel(s: ChangeSource) {
  return s === 'CHANGE_REQUEST' ? 'Change request' : 'Team feedback'
}
</script>
