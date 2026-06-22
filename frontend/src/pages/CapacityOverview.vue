<template>
  <div>
    <h1 class="section-title">Capacity Overview</h1>

    <div class="controls">
      <label>Granularity
        <select :value="planning.granularity" @change="planning.setGranularity(($event.target as HTMLSelectElement).value as Granularity)">
          <option value="WEEK">Week</option>
          <option value="MONTH">Month</option>
          <option value="QUARTER">Quarter</option>
        </select>
      </label>
      <label>From
        <input type="date" v-model="planning.horizonStart" @change="planning.reloadTeams()" />
      </label>
      <label>To
        <input type="date" v-model="planning.horizonEnd" @change="planning.reloadTeams()" />
      </label>
    </div>

    <div v-if="planning.loading" class="loading">Loading…</div>

    <div v-else-if="!planning.teams.length" class="empty">No teams found.</div>

    <div v-else class="grid-2">
      <div v-for="team in planning.teams" :key="team.id" class="card team-card">
        <div class="team-header">
          <span class="team-name">{{ team.name }}</span>
          <span class="member-count">{{ team.memberCount }} members</span>
        </div>

        <div class="flag-row" v-if="team.flags.length">
          <span
            v-for="flag in team.flags"
            :key="flag.type + flag.entityId"
            :class="['tag', flag.severity === 'RED' ? 'tag-red' : 'tag-amber']"
          >{{ flagLabel(flag.type) }}</span>
        </div>

        <VChart v-if="team.capacityByPeriod.length" class="capacity-chart" :option="chartOption(team)" autoresize />

        <div class="period-stats" v-if="team.capacityByPeriod.length">
          <div class="stat">
            <span class="stat-label">Total capacity</span>
            <span class="stat-value">{{ totalCapacity(team) }} pd</span>
          </div>
          <div class="stat">
            <span class="stat-label">Total load</span>
            <span class="stat-value">{{ totalLoad(team) }} pd</span>
          </div>
          <div class="stat">
            <span class="stat-label">Gap</span>
            <span :class="['stat-value', totalGap(team) < 0 ? 'over' : 'ok']">{{ totalGap(team) }} pd</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { usePlanningStore } from '@/stores/planning'
import type { TeamSummary, FlagType, Granularity } from '@/types'

const planning = usePlanningStore()

function flagLabel(type: FlagType): string {
  const map: Record<FlagType, string> = {
    OVER_ALLOCATION:   'Over-allocated',
    SKILL_SHORTFALL:   'Skill gap',
    DECOMPOSITION_GAP: 'Decomp gap',
    CRITICAL_RESOURCE: 'Critical resource',
  }
  return map[type] ?? type
}

function chartOption(team: TeamSummary) {
  const periods = team.capacityByPeriod
  return {
    grid: { top: 8, bottom: 32, left: 40, right: 8, containLabel: false },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const p = periods[params[0].dataIndex]
        return `<b>${p.label}</b><br/>Capacity: ${p.effectiveCapacityPd} pd<br/>Load: ${p.committedLoadPd} pd<br/>Gap: ${p.capacityGapPd} pd`
      },
    },
    xAxis: {
      type: 'category',
      data: periods.map(p => p.label),
      axisLabel: { fontSize: 10 },
    },
    yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
    series: [
      {
        name: 'Capacity',
        type: 'bar',
        barGap: '-100%',
        data: periods.map(p => p.effectiveCapacityPd),
        itemStyle: { color: 'rgba(13, 148, 136, 0.35)' },
        z: 1,
      },
      {
        name: 'Load',
        type: 'bar',
        barGap: '-100%',
        data: periods.map(p => ({
          value: p.committedLoadPd,
          itemStyle: { color: p.overAllocated ? 'rgba(239,68,68,0.8)' : 'rgba(59,130,246,0.8)' },
        })),
        z: 2,
      },
    ],
  }
}

function totalCapacity(team: TeamSummary) {
  return team.capacityByPeriod.reduce((s, p) => s + p.effectiveCapacityPd, 0).toFixed(1)
}
function totalLoad(team: TeamSummary) {
  return team.capacityByPeriod.reduce((s, p) => s + p.committedLoadPd, 0).toFixed(1)
}
function totalGap(team: TeamSummary) {
  return parseFloat((parseFloat(totalCapacity(team)) - parseFloat(totalLoad(team))).toFixed(1))
}
</script>

<style scoped>
.team-card { display: flex; flex-direction: column; gap: .75rem; }

.team-header { display: flex; justify-content: space-between; align-items: baseline; }
.team-name { font-weight: 600; font-size: 1rem; }
.member-count { font-size: .75rem; color: var(--c-muted); }

.flag-row { display: flex; flex-wrap: wrap; gap: .25rem; }

.capacity-chart { height: 140px; }

.period-stats { display: flex; gap: 1rem; border-top: 1px solid var(--c-border); padding-top: .5rem; }
.stat { display: flex; flex-direction: column; gap: 2px; }
.stat-label { font-size: .7rem; color: var(--c-muted); text-transform: uppercase; }
.stat-value { font-size: .875rem; font-weight: 600; }
.stat-value.over { color: var(--c-red); }
.stat-value.ok   { color: var(--c-teal); }
</style>
