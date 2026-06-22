# API Reference

Base URL: `http://localhost:8080/api/v1`
Auth: HTTP Basic (`admin` / `admin` for local dev — see `application.yml`)
Content-Type: `application/json`

---

## Teams

### `GET /teams`
Returns all teams with capacity breakdowns.

Query params:
| Param | Default | Values |
|---|---|---|
| `granularity` | `MONTH` | `WEEK` \| `MONTH` \| `QUARTER` |
| `from` | required | ISO date `2025-01-01` |
| `to` | required | ISO date `2025-12-31` |

Response: `TeamSummaryDto[]`

### `GET /teams/:id`
Single team. Same query params as above.

Response: `TeamSummaryDto`

### `POST /teams`
Body: `{ name, overheadFactor?, supportFactor? }`

### `PUT /teams/:id`
Body: partial `Team`

---

## Persons

### `GET /persons`
Query params: `teamId?` (filter by team)

Response: `Person[]`

### `GET /persons/:id`
### `POST /persons`
Body: `{ name, teamId, baseAvailability?, skills? }`

### `PUT /persons/:id`

### `POST /persons/:id/availability-overrides`
Adds a time-boxed availability deviation. This is also the input to
what-if scenarios — apply an override with `factor: 0` from a future date
to model a departure.

Body:
```json
{
  "startDate": "2025-07-01",
  "endDate": "2025-09-30",    // null = permanent
  "factor": 0.0,
  "reason": "Parental leave"
}
```

### `DELETE /persons/:id/availability-overrides/:index`
Removes the override at the given list index.

---

## Initiatives

### `GET /initiatives`
Response: `InitiativeSummaryDto[]` — includes `decompositionGapPd` and `flags`

### `GET /initiatives/:id`
### `POST /initiatives`
Body: `{ name, topDownEstimate, targetDeliveryDate, priority?, ownerName?, status? }`

### `PUT /initiatives/:id`

---

## Epics

### `GET /epics`
Query params: `teamId?`, `initiativeId?`

Response: `EpicSummaryDto[]` — includes `skillShortfalls` and `isAtRisk`

### `GET /epics/:id`
### `POST /epics`
Body:
```json
{
  "name": "SEPA Instant API",
  "teamId": "...",
  "initiativeId": "...",       // nullable
  "estimate": 60,
  "startDate": "2025-01-15",
  "dueDate": "2025-03-31",
  "priority": "MUST",          // MUST | SHOULD | COULD | WONT
  "status": "COMMITTED",
  "requiredSkills": [          // optional
    { "skillId": "...", "minProficiency": "PROFICIENT", "demandPd": 40 }
  ]
}
```

### `PUT /epics/:id`
### `DELETE /epics/:id`

---

## Scenarios

### `POST /scenarios/preview`
Previews impact of proposed changes WITHOUT committing. Expensive — debounce in UI.

Body:
```json
{
  "deltas": [
    { "entityType": "Epic", "entityId": "...", "field": "dueDate", "newValue": "2025-04-30" },
    { "entityType": "Epic", "entityId": "...", "field": "status",  "newValue": "DEFERRED" }
  ]
}
```

Response: `ScenarioImpactDto`
```json
{
  "scenarioId": null,
  "newFlags": [...],       // flags raised vs baseline
  "resolvedFlags": [...],  // flags cleared vs baseline
  "capacityDeltas": [...]  // per-team load changes
}
```

### `POST /scenarios/commit`
Applies deltas to the database and writes a ChangeSet. `reason` is required.

Body:
```json
{
  "deltas": [...],
  "reason": "Q2 replan after new EU regulation requirement",
  "source": "CHANGE_REQUEST"   // CHANGE_REQUEST | TEAM_FEEDBACK
}
```

Response: `ChangeSetDto`

---

## Change log

### `GET /changelog`
All change sets, newest first.

Response: `ChangeSetDto[]`

### `GET /changelog/entity?entityType=Epic&entityId=:id`
Change history for a specific entity.

---

## DTO shapes

### `TeamSummaryDto`
```ts
{
  id, name, overheadFactor, supportFactor, memberCount,
  capacityByPeriod: PeriodCapacityDto[],
  flags: RiskFlagDto[]
}
```

### `PeriodCapacityDto`
```ts
{
  periodStart, periodEnd,
  label,              // "Jan 2025" | "Q1 2025" | "W04 2025"
  rawCapacityPd,
  effectiveCapacityPd,
  committedLoadPd,
  capacityGapPd,      // negative = over-allocated
  overAllocated       // true when capacityGapPd < 0
}
```

### `RiskFlagDto`
```ts
{
  type: 'OVER_ALLOCATION' | 'SKILL_SHORTFALL' | 'DECOMPOSITION_GAP' | 'CRITICAL_RESOURCE',
  severity: 'RED' | 'AMBER',
  message,       // human-readable description
  entityType,    // "Team" | "Epic" | "Initiative" | "Person"
  entityId
}
```

### `InitiativeSummaryDto`
```ts
{
  id, name, status, priority,
  topDownEstimatePd, bottomUpEstimatePd, decompositionGapPd,
  teamsInvolved: string[],   // team UUIDs
  flags: RiskFlagDto[]
}
```

### `EpicSummaryDto`
```ts
{
  id, name, teamId, initiativeId?,
  priority, status, estimatePd, startDate, dueDate,
  skillShortfalls: SkillShortfallDto[],
  isAtRisk
}
```

### `SkillShortfallDto`
```ts
{
  skillId, skillName, minProficiency,
  demandPd, availablePd, gapPd
}
```
