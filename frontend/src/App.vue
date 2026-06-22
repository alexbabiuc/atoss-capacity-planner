<template>
  <div id="shell">
    <nav class="navbar">
      <span class="brand">Capacity Planner</span>
      <div class="nav-links">
        <RouterLink to="/">Overview</RouterLink>
        <RouterLink to="/initiatives">Initiatives</RouterLink>
        <RouterLink to="/scenarios">Scenarios</RouterLink>
        <RouterLink to="/changelog">Change Log</RouterLink>
      </div>
      <div class="flag-counts">
        <span v-if="planning.redFlags.length" class="badge red">
          🔴 {{ planning.redFlags.length }}
        </span>
        <span v-if="planning.amberFlags.length" class="badge amber">
          🟡 {{ planning.amberFlags.length }}
        </span>
        <span v-if="!planning.hasRisks && !planning.loading" class="badge ok">✓ No risks</span>
      </div>
    </nav>

    <main class="page-body">
      <div v-if="planning.error" class="global-error">{{ planning.error }}</div>
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { usePlanningStore } from '@/stores/planning'

const planning = usePlanningStore()
onMounted(() => planning.loadAll())
</script>

<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
  --c-primary: #1e3a5f;
  --c-accent:  #3b82f6;
  --c-teal:    #0d9488;
  --c-red:     #ef4444;
  --c-amber:   #f59e0b;
  --c-bg:      #f4f6f9;
  --c-surface: #ffffff;
  --c-border:  #e2e8f0;
  --c-text:    #1e293b;
  --c-muted:   #64748b;
  font-family: system-ui, -apple-system, sans-serif;
  color: var(--c-text);
  background: var(--c-bg);
}

.navbar {
  position: sticky; top: 0; z-index: 100;
  display: flex; align-items: center; gap: 1.5rem;
  background: var(--c-primary); color: #fff;
  padding: 0 1.5rem; height: 52px;
}
.brand { font-weight: 700; font-size: 1rem; white-space: nowrap; }
.nav-links { display: flex; gap: 1rem; flex: 1; }
.nav-links a { color: #cbd5e1; text-decoration: none; font-size: .875rem; padding: 4px 8px; border-radius: 4px; }
.nav-links a:hover, .nav-links a.router-link-active { color: #fff; background: rgba(255,255,255,.15); }
.flag-counts { display: flex; gap: .5rem; }
.badge { padding: 2px 10px; border-radius: 999px; font-size: .75rem; font-weight: 600; }
.badge.red   { background: var(--c-red); color: #fff; }
.badge.amber { background: var(--c-amber); color: #fff; }
.badge.ok    { background: var(--c-teal); color: #fff; }

.page-body { padding: 1.5rem; max-width: 1280px; margin: 0 auto; }

.global-error { background: #fee2e2; border: 1px solid var(--c-red); color: #991b1b;
  padding: .75rem 1rem; border-radius: 6px; margin-bottom: 1rem; }

/* Shared utility classes used across pages */
.card { background: var(--c-surface); border: 1px solid var(--c-border); border-radius: 8px; padding: 1rem; }
.section-title { font-size: 1.125rem; font-weight: 600; margin-bottom: 1rem; }

table { width: 100%; border-collapse: collapse; font-size: .875rem; }
th { text-align: left; padding: .5rem .75rem; background: #f8fafc; color: var(--c-muted);
  font-weight: 600; font-size: .75rem; text-transform: uppercase; border-bottom: 1px solid var(--c-border); }
td { padding: .5rem .75rem; border-bottom: 1px solid var(--c-border); }
tr:last-child td { border-bottom: none; }
tr:hover td { background: #f8fafc; }

.tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: .75rem; font-weight: 600; }
.tag-red    { background: #fee2e2; color: #991b1b; }
.tag-amber  { background: #fef3c7; color: #92400e; }
.tag-green  { background: #d1fae5; color: #065f46; }
.tag-blue   { background: #dbeafe; color: #1e40af; }
.tag-gray   { background: #f1f5f9; color: var(--c-muted); }

a { color: var(--c-accent); text-decoration: none; }
a:hover { text-decoration: underline; }

.controls { display: flex; gap: 1rem; align-items: center; flex-wrap: wrap; margin-bottom: 1.5rem; }
.controls label { font-size: .875rem; color: var(--c-muted); display: flex; flex-direction: column; gap: 2px; }
.controls select, .controls input[type=date] {
  border: 1px solid var(--c-border); border-radius: 4px; padding: 4px 8px;
  font-size: .875rem; background: #fff; }

.btn { padding: 6px 14px; border-radius: 6px; border: none; font-size: .875rem;
  font-weight: 500; cursor: pointer; }
.btn-primary { background: var(--c-accent); color: #fff; }
.btn-primary:hover { background: #2563eb; }
.btn-danger { background: var(--c-red); color: #fff; }
.btn-danger:hover { background: #dc2626; }
.btn-outline { background: transparent; border: 1px solid var(--c-border); color: var(--c-text); }
.btn-outline:hover { background: #f8fafc; }

.grid-2 { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 1rem; }
.grid-3 { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }

.empty { text-align: center; color: var(--c-muted); padding: 3rem 0; font-size: .875rem; }
.loading { text-align: center; color: var(--c-muted); padding: 3rem 0; }
</style>
