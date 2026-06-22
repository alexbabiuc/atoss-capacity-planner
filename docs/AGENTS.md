# Capacity Planner — AI Agent Guide

This file is the entry point for any AI coding agent working on this repository.
Read it first. It tells you where everything lives, what the architecture rules are,
and which tasks are ready to implement next.

---

## Repository layout

```
capacity-planner/
├── backend/                  Java 17 + Spring Boot 3.3 + PostgreSQL
│   ├── src/main/java/com/capacityplanner/
│   │   ├── entity/           JPA entities — stored fields only, no derived state
│   │   ├── repository/       Spring Data repositories (one public interface per file)
│   │   ├── service/
│   │   │   ├── DerivationService.java   ← pure math engine, no DB writes, fully tested
│   │   │   ├── PeriodSlicer.java        ← WEEK/MONTH/QUARTER period slicing utility
│   │   │   └── PlanningService.java     ← orchestration layer, fully implemented
│   │   ├── controller/       REST controllers, one per domain object
│   │   ├── dto/              Response DTOs (Dtos.java = one file, Java records)
│   │   └── config/           SecurityConfig (Basic auth + CORS)
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__initial_schema.sql   ← complete schema
│           └── V2__seed_data.sql        ← 3 teams, 14 people, 4 initiatives, 12 epics
│
├── frontend/                 Vue 3 + TypeScript + Pinia + ECharts
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src/
│       ├── main.ts           App entry — registers ECharts components, Pinia, Router
│       ├── router.ts         6 routes (overview, initiatives, epics, scenarios, changelog)
│       ├── App.vue           Top navbar with live RED/AMBER flag counters, RouterView
│       ├── pages/
│       │   ├── CapacityOverview.vue   Team cards + overlaid ECharts bar chart
│       │   ├── InitiativeList.vue     Table with gap/status badges
│       │   ├── InitiativeDetail.vue   Gap meter, flags, epic table
│       │   ├── EpicDetail.vue         Skill shortfall table, risk banner
│       │   ├── ScenarioBuilder.vue    Delta editor + impact preview + commit form
│       │   └── ChangeLog.vue          Table with source badges
│       ├── types/index.ts    TypeScript types — mirrors backend DTOs exactly
│       ├── api/client.ts     Axios wrapper — Basic auth admin/admin, all HTTP calls
│       └── stores/planning.ts  Pinia stores (planning + scenario)
│
├── docs/
│   ├── AGENTS.md             ← you are here
│   ├── ARCHITECTURE.md       Decisions, rules, deferred items
│   └── API.md                Endpoint reference
```

---

## The single most important rule

**`DerivationService` never writes to the database.**

All derived values (capacity gaps, risk flags, epic loads, scenario impact) are
computed in `DerivationService` and returned in DTOs. They are never stored as
columns. This guarantees views can never disagree. Do not break this contract.

---

## What to implement next (priority order)

### ~~1. `PlanningService` — fill in the TODO stubs~~ ✅ Done

All controller logic is wired; `PlanningService` stubs throw `UnsupportedOperationException`.
Implement each method in this order:

**a. `getTeamSummary(id, granularity, from, to)`**
```
1. Load team by id (teamRepository)
2. Load members (personRepository.findByTeamId)
3. Slice [from, to] into periods using the granularity enum:
   WEEK  → 7-day windows (Mon–Sun)
   MONTH → calendar months
   QUARTER → 3-month windows
4. For each period, call:
   - derivation.effectiveCapacity(team, members, periodStart, periodEnd)
   - epicsRepository.findActiveEpicsForTeamInPeriod(teamId, from, to)
   - derivation.committedLoad(epics, periodStart, periodEnd)
   - Build PeriodCapacityDto
5. Evaluate risk flags:
   - RED overAllocation: any period where capacityGap < 0
   - RED skillShortfall: derivation.skillShortfalls(epic, members) for each epic
   - AMBER criticalResource: derivation.isCriticalResource(person, members, epics, horizonEnd)
6. Return TeamSummaryDto
```

**b. `getTeamSummaries`** — calls `getTeamSummary` for each team in parallel.

**c. `getInitiativeSummary(id)`**
```
1. Load initiative
2. Load epics by initiativeId
3. Call derivation.decompositionGap(initiative, epics)
4. Collect distinct teamIds from epics → teamsInvolved
5. AMBER flag if decompositionGap > 0
6. Return InitiativeSummaryDto
```

**d. `getEpicSummary(id)`**
```
1. Load epic + team members
2. Call derivation.skillShortfalls(epic, members)
3. Set isAtRisk = !shortfalls.isEmpty() || team is over-allocated in epic's window
4. Return EpicSummaryDto
```

**e. `previewScenario(request)`**
```
1. Load baseline state (all entities touched by deltas)
2. Apply deltas IN MEMORY only — do NOT call repository.save()
3. Recompute risk flags on the modified state
4. Load baseline flags (from current persisted state)
5. Diff: newFlags = scenario flags not in baseline; resolvedFlags = baseline flags not in scenario
6. Build TeamCapacityDelta for each team touched
7. Return ScenarioImpactDto — no DB writes
```

**f. `commitScenario(request)`**
```
1. Validate reason is non-empty (throw IllegalArgumentException if blank)
2. For each delta: load entity, read oldValue, apply newValue, save
3. Build ChangeSet with all LoggedDeltas (oldValue captured in step 2)
4. changeSetRepository.save(changeSet)
5. Return ChangeSetDto
```

**g. `updateTeam`, `updatePerson`, `updateInitiative`, `updateEpic`**
```
Standard: load by id, apply non-null patch fields, save.
For updates that should be tracked: route through a single-delta commitScenario call
or write a ChangeSet directly. Keep tracked vs untracked updates consistent.
```

---

### ~~2. Period slicing utility~~ ✅ Done

Used by `getTeamSummary` and anywhere `granularity` appears.

```java
// Suggested location: com.capacityplanner.service.PeriodSlicer (new class)

List<PeriodBounds> slice(LocalDate from, LocalDate to, Granularity granularity)

record PeriodBounds(LocalDate start, LocalDate end, String label) {}

// Labels:
//   WEEK    → "W04 2025" (ISO week)
//   MONTH   → "Jan 2025"
//   QUARTER → "Q1 2025"
```

---

### ~~3. Frontend pages~~ ✅ Done

Build in this order — each page has its backing store and API already wired:

| Page | Route | Key component |
|---|---|---|
| Capacity overview | `/` | Team cards, ECharts stacked-bar per period |
| Initiative list | `/initiatives` | Table, decomposition gap badge |
| Initiative detail | `/initiatives/:id` | Epic list, gap meter, flag list |
| Epic detail | `/epics/:id` | Skill shortfall list, timeline |
| Scenario builder | `/scenarios` | Delta editor, impact preview panel |
| Change log | `/changelog` | Table with source badge, reason text |

**ECharts pattern for capacity bar chart:**
```ts
// x-axis: period labels from PeriodCapacity[].label
// series 1: effectiveCapacityPd (bar, light teal)
// series 2: committedLoadPd     (bar, overlaid or stacked)
// markLine: at committedLoadPd = effectiveCapacityPd (threshold)
// bar color override: red when overAllocated = true
```

---

### ~~4. Repositories — make public~~ ✅ Done

`Repositories.java` defines all repositories as package-private interfaces.
Make them `public` (or split into separate files if the IDE complains about
multiple public types in one file).

---

## Architecture rules (do not violate)

1. **DerivationService never writes to DB.** All computed values are transient.
2. **Flyway owns DDL.** Never set `ddl-auto` to anything except `validate` (prod) or `create-drop` (test).
3. **Effort unit is person-days throughout.** Never introduce story points or hours.
4. **Granularity is a view lens.** Data is stored at day resolution; weeks/months/quarters are aggregations.
5. **Reason required on every commit.** `commitScenario` must reject blank reasons.
6. **One scenario at a time.** No branching; `previewScenario` is always against the current persisted baseline.
7. **Epic belongs to exactly one team.** Cross-team work is expressed as multiple epics under one initiative.
8. **ChangeSet is append-only.** Never update or delete a ChangeSet row.

---

## Running locally

> **Build note:** `pom.xml` targets Java 17. Lombok 1.18.32 (bundled with Spring Boot 3.3)
> does not support Java 21+. If your active JDK is newer, set `JAVA_HOME` to a Java 17
> installation before running Maven.
> ```bash
> export JAVA_HOME=/Library/Java/JavaVirtualMachines/sapmachine-17.jdk/Contents/Home
> ```

```bash
# 1. Start Postgres
docker run -d --name cp-db \
  -e POSTGRES_DB=capacity_planner \
  -e POSTGRES_USER=capacity_planner \
  -e POSTGRES_PASSWORD=capacity_planner \
  -p 5432:5432 postgres:16

# 2. Start backend (Flyway runs migrations + seed on first start)
cd backend
mvn spring-boot:run

# 3. Start frontend
cd frontend
npm install
npm run dev
# → http://localhost:5173
# Backend → http://localhost:8080
# Basic auth: admin / admin (see application.yml)

# 4. Run backend tests
cd backend
./mvnw test
```

---

## Seed data summary (V2 migration)

Designed to demonstrate all four risk flags out of the box:

| Flag | Triggered by |
|---|---|
| 🔴 Over-allocation | Payments team in Feb–Mar 2025 (SEPA epics overlap, tight window) |
| 🔴 Skill shortfall | Platform: Kubernetes epic demands Expert K8s; only Frank has it |
| 🟡 Critical resource | Frank Müller — sole Expert K8s holder on Platform team |
| 🟡 Decomposition gap | "Data Warehouse Modernisation": 300pd top-down, 160pd in epics |

Alice Chen (Payments domain expert) has a parental-leave override in Q3 2025,
which triggers the critical-resource flag for Payments Domain epics in that period.

---

## Key design decisions (see ARCHITECTURE.md for full rationale)

| Decision | Choice | Reason |
|---|---|---|
| Initiative vs epic estimates | Independent, gap surfaced | Gap is a signal — missing decomposition or hidden scope |
| Effort unit | Person-days | Aggregates across teams; story points don't |
| Time model | Continuous dates, aggregated on read | Single source of truth; granularity is a lens |
| Effort distribution | Even spread across working days | Transparent default; curves deferred |
| Scenario model | One active scenario, in-memory overlay | Avoids branching complexity; still satisfies preview + commit |
| Priority model | MoSCoW; WONT = explicit undeliverable state | Directly models the "some epics become undeliverable" requirement |
| Risk flags | RED = infeasible, AMBER = fragile | Tells each stakeholder whether to act now or watch |
