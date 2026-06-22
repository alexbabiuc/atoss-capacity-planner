// types/index.ts — mirrors backend DTOs exactly

export type InitiativeStatus = 'PROPOSED' | 'COMMITTED' | 'AT_RISK' | 'DELIVERED' | 'CANCELLED'
export type EpicPriority      = 'MUST' | 'SHOULD' | 'COULD' | 'WONT'
export type EpicStatus        = 'COMMITTED' | 'AT_RISK' | 'DEFERRED' | 'DONE'
export type Proficiency       = 'FAMILIAR' | 'PROFICIENT' | 'EXPERT'
export type ChangeSource      = 'CHANGE_REQUEST' | 'TEAM_FEEDBACK'
export type Granularity       = 'WEEK' | 'MONTH' | 'QUARTER'
export type FlagType          = 'OVER_ALLOCATION' | 'SKILL_SHORTFALL' | 'DECOMPOSITION_GAP' | 'CRITICAL_RESOURCE'
export type Severity          = 'RED' | 'AMBER'

export interface Skill {
  id: string
  name: string
  category?: string
}

export interface PersonSkill {
  skill: Skill
  proficiency: Proficiency
}

export interface AvailabilityOverride {
  startDate: string
  endDate?: string
  factor: number
  reason: string
}

export interface Person {
  id: string
  name: string
  teamId: string
  baseAvailability: number
  skills: PersonSkill[]
  availabilityOverrides: AvailabilityOverride[]
}

export interface Team {
  id: string
  name: string
  overheadFactor: number
  supportFactor: number
}

export interface PeriodCapacity {
  periodStart: string
  periodEnd: string
  label: string
  rawCapacityPd: number
  effectiveCapacityPd: number
  committedLoadPd: number
  capacityGapPd: number
  overAllocated: boolean
}

export interface RiskFlag {
  type: FlagType
  severity: Severity
  message: string
  entityType: string
  entityId: string
}

export interface TeamSummary extends Team {
  memberCount: number
  capacityByPeriod: PeriodCapacity[]
  flags: RiskFlag[]
}

export interface Initiative {
  id: string
  name: string
  description?: string
  topDownEstimatePd: number
  startDate?: string
  targetDeliveryDate: string
  priority: number
  ownerName?: string
  status: InitiativeStatus
}

export interface InitiativeSummary extends Initiative {
  bottomUpEstimatePd: number
  decompositionGapPd: number
  teamsInvolved: string[]
  flags: RiskFlag[]
}

export interface SkillShortfall {
  skillId: string
  skillName: string
  minProficiency: Proficiency
  demandPd: number
  availablePd: number
  gapPd: number
}

export interface EpicSummary {
  id: string
  name: string
  teamId: string
  initiativeId?: string
  priority: EpicPriority
  status: EpicStatus
  estimatePd: number
  startDate: string
  dueDate: string
  skillShortfalls: SkillShortfall[]
  isAtRisk: boolean
}

export interface TeamCapacityDelta {
  teamId: string
  teamName: string
  periodStart: string
  periodEnd: string
  baselineLoadPd: number
  scenarioLoadPd: number
  deltaLoadPd: number
}

export interface ScenarioImpact {
  scenarioId?: string
  newFlags: RiskFlag[]
  resolvedFlags: RiskFlag[]
  capacityDeltas: TeamCapacityDelta[]
}

export interface ChangeSetSummary {
  id: string
  timestamp: string
  actor: string
  source: ChangeSource
  reason: string
  changeCount: number
}
