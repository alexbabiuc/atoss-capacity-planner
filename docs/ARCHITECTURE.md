# Architecture & Design Decisions

Full rationale for every significant decision made during design.
Each section answers: what we decided, why, and what we deliberately left out.

---

## Domain model

### Hierarchy
Initiative → Epic. Epics are the atomic unit of work. Stories, tasks, and sprints
are explicitly out of scope — this tool operates at strategic altitude.

An initiative may involve multiple teams; an epic belongs to exactly one team.
Cross-team work is expressed as sibling epics under a shared initiative.

### Estimate model (Decision 1)
Initiatives and epics carry **independent** estimates:
- `Initiative.topDownEstimate` — a strategic top-down number set by leadership/PM.
- `Σ(epic.estimate)` — the bottom-up sum derived from actual scoping.

The **decomposition gap** = topDown − bottomUp. A positive gap means scope not yet
planned; a negative gap means the epics exceed the strategic envelope. Both are signals.

Alternative considered: pure roll-up (initiative estimate = sum of epics). Rejected
because you cannot estimate an initiative before decomposing it, and the gap between
leadership's number and the team's number is itself valuable planning information.

### Effort unit
**Person-days (PD)** throughout the system. Story points were considered and rejected:
they are team-relative velocity proxies, not transferable across teams, and cannot
be summed at the initiative level to produce cross-team capacity comparisons.

---

## Capacity model

### The focus-factor formula
```
effectiveCapacity = rawCapacity × (1 − overheadFactor − supportFactor)
```
Teams carry two named fractions — overhead (ceremonies, 1:1s) and support (on-call,
interrupts) — subtracted from raw capacity. At defaults (0.15 + 0.10 = 0.25), the
implied focus factor is 0.75. Industry benchmarks put realistic engineering focus at
50–75%, so 0.75 is the optimistic end; teams with heavier on-call should raise supportFactor.

### Time model
The canonical store is at **working-day resolution** (Mon–Fri, excluding holidays).
Weeks, months, and quarters are aggregations over this store. This means:
- A single data source; views at different granularities can never disagree.
- Over-allocation is detectable at any zoom (a team that looks fine at the quarter
  level can be badly overloaded in a specific month).
- Configurable granularity is free: it's a slicing function, not a schema change.

Working days currently excludes weekends only. A holiday calendar is planned but deferred.

### Effort distribution
Epic effort is distributed **evenly** across working days between startDate and dueDate.
This is the transparent, defensible default. Real epics ramp up and taper, but modeling
effort curves would require per-period stored allocations — deferred to a future milestone.
The even-spread assumption is documented here and in the design document.

---

## Risk flags

Four flags, two severities:

| Flag | Severity | Meaning |
|---|---|---|
| Over-allocation | 🔴 RED | Team demand > effective capacity in a period. Plan is infeasible as drawn. |
| Skill shortfall | 🔴 RED | Epic requires a skill the assigned team cannot staff at required proficiency. |
| Decomposition gap | 🟡 AMBER | Initiative's top-down estimate exceeds sum of epics. Scope not fully planned. |
| Critical resource | 🟡 AMBER | A person holds a scarce skill required by committed epics (bus factor ≤ 1). |

RED = hard constraint (cannot deliver without a change). AMBER = warning (plan is fragile
or incomplete, but not immediately broken). This distinction tells each stakeholder whether
to act now or put it on their watchlist.

### Critical resource derivation
A person is flagged as a critical resource when both conditions hold:
1. They hold a skill at a proficiency level required by at least one committed epic.
2. They are the only available holder of that skill on the team (availableHolders ≤ configurable threshold, default 1).

"Available" means `totalAvailablePd(person, today, horizonEnd) > 0`.
This is derived entirely from the stored skills model and availability overrides —
no extra input required from the user.

---

## Change management

### Baseline / scenario / commit loop
1. The current persisted state IS the baseline.
2. A scenario is a list of proposed deltas applied in memory (no DB writes).
3. Impact is the diff of risk flags between the scenario state and the baseline.
4. Committing applies deltas to DB and writes a ChangeSet.

One active scenario at a time. Branching, named scenarios, and concurrent scenario
management are deferred — they require merge-conflict resolution and a significantly
more complex UI, neither of which fits a prototype.

### ChangeSet (append-only)
Every committed change produces a ChangeSet row. Once written, ChangeSets are never
updated or deleted (`@Immutable` in Hibernate, append-only semantics in the schema).

`source` distinguishes:
- `CHANGE_REQUEST` — top-down: leadership or PM adds/reshapes demand.
- `TEAM_FEEDBACK` — bottom-up: team re-estimates, reports a slip, flags lost capacity.

`reason` is mandatory and non-empty. This directly addresses the brief's stated pain:
"planning breaks when updates are made across teams" and nobody can reconstruct why.

---

## Skill model

Skills are a constraint overlay on top of the scalar capacity model, not a
per-skill capacity solver. An epic optionally declares required skills with a
minimum proficiency and a demand in person-days. The tool flags a shortfall when the
team's skilled capacity (members meeting the proficiency threshold) is less than
the demand. It does not assign specific people to epics — that would push below
the epic-level planning altitude the tool is designed for.

Proficiency uses a deliberate 3-level ordinal scale (Familiar / Proficient / Expert).
Finer scales (1–10, percentages) were considered and rejected: they imply false precision
at strategic planning altitude and complicate the comparison logic without adding real value.

---

## Tech stack decisions

| Choice | Rationale |
|---|---|
| Java 26 + Spring Boot 4.1 | Strong typing maps well to the schema; JPA handles relationships; mature ecosystem. |
| PostgreSQL | Native date arithmetic (`generate_series`), window functions for period sums, reliable concurrent writes for the append-only log. |
| Flyway (not ddl-auto) | The ChangeSet table is append-only by design — Hibernate's `create-drop` would destroy it on restart. Flyway owns DDL, Hibernate validates only. |
| Vue 3 + Pinia | Lightweight enough to move fast; Composition API maps cleanly to the derived-state model; Pinia integrates well with async loading patterns. |
| ECharts (not Chart.js) | Better support for stacked-bar-with-threshold patterns; the capacity-vs-load chart needs color overrides per bar (red when over-allocated) which ECharts handles cleanly. |
| REST (not GraphQL) | The API surface is well-defined and stable; GraphQL adds tooling overhead without buying meaningful flexibility here. |
| Basic auth (not JWT) | Prototype scope. Actor identity for the ChangeSet is the only auth requirement. Three in-memory users (one per stakeholder role) are sufficient for demo purposes. |

---

## Deliberately deferred

These are the honest "what breaks first at scale" and "what would you build next" answers:

1. **Cross-epic dependencies.** The most significant omission. A team can be under
   capacity yet fully blocked waiting on another team's epic. Sequencing and dependency
   modeling would require a DAG structure, critical-path computation, and a substantially
   more complex planning view.

2. **Effort curves.** Even spread is the assumption. Production planning tools allow
   front-loading, tapering, and milestone-gated profiles. Requires stored Allocation
   records per (epic, period).

3. **Holiday calendars.** Per-team and per-person calendars for regional holidays.
   Currently only weekends are excluded.

4. **Multi-scenario branching.** Named scenarios, comparison views, merge resolution.

5. **Role-based access control.** Higher management should not be able to edit team
   capacity; team leads should not be able to edit initiative priorities.

6. **Live data integrations.** Jira import for epic data, HR system feed for availability.

7. **Cost / budget dimension.** A natural extension once PD effort is modeled well.
