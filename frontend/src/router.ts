import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',                    component: () => import('./pages/CapacityOverview.vue') },
    { path: '/initiatives',         component: () => import('./pages/InitiativeList.vue') },
    { path: '/initiatives/:id',     component: () => import('./pages/InitiativeDetail.vue') },
    { path: '/epics/:id',           component: () => import('./pages/EpicDetail.vue') },
    { path: '/scenarios',           component: () => import('./pages/ScenarioBuilder.vue') },
    { path: '/changelog',           component: () => import('./pages/ChangeLog.vue') },
    { path: '/admin/teams',         component: () => import('./pages/TeamsAdmin.vue') },
    { path: '/admin/people',        component: () => import('./pages/PeopleAdmin.vue') },
  ],
})

export default router
