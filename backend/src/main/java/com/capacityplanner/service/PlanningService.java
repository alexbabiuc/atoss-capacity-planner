package com.capacityplanner.service;

import com.capacityplanner.controller.ScenarioController.DeltaRequest;
import com.capacityplanner.controller.ScenarioController.ScenarioCommitRequest;
import com.capacityplanner.controller.ScenarioController.ScenarioPreviewRequest;
import com.capacityplanner.dto.*;
import com.capacityplanner.entity.*;
import com.capacityplanner.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestration layer between controllers and the derivation engine.
 *
 * Responsibilities:
 *   - Accept request DTOs from controllers (never raw entities)
 *   - Load and mutate stored entities via repositories
 *   - Invoke DerivationService to compute derived fields
 *   - Assemble and return response DTOs (never raw entities with lazy proxies)
 *   - Write ChangeSets on mutations
 */
@Service
@RequiredArgsConstructor
public class PlanningService {

    private final TeamRepository        teamRepository;
    private final PersonRepository      personRepository;
    private final EpicRepository        epicRepository;
    private final InitiativeRepository  initiativeRepository;
    private final ChangeSetRepository   changeSetRepository;
    private final SkillRepository       skillRepository;
    private final DerivationService     derivation;

    // ── Teams ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TeamSummaryDto> getTeamSummaries(String granularity, LocalDate from, LocalDate to) {
        return teamRepository.findAll().parallelStream()
            .map(t -> getTeamSummary(t.getId(), granularity, from, to))
            .toList();
    }

    @Transactional(readOnly = true)
    public TeamSummaryDto getTeamSummary(UUID id, String granularity, LocalDate from, LocalDate to) {
        Team team = teamRepository.findById(id).orElseThrow();
        List<Person> members = personRepository.findByTeamId(id);
        List<Epic> allEpics = epicRepository.findActiveEpicsForTeamInPeriod(id, from, to);

        List<PeriodCapacityDto> capacityByPeriod = PeriodSlicer.slice(from, to, granularity).stream()
            .map(p -> {
                List<Epic> periodEpics = epicRepository.findActiveEpicsForTeamInPeriod(id, p.start(), p.end());
                double rawCap = derivation.rawCapacity(members, p.start(), p.end());
                double effCap = derivation.effectiveCapacity(team, members, p.start(), p.end());
                double load   = derivation.committedLoad(periodEpics, p.start(), p.end());
                double gap    = effCap - load;
                return new PeriodCapacityDto(p.start(), p.end(), p.label(), rawCap, effCap, load, gap, gap < 0);
            })
            .toList();

        List<RiskFlagDto> flags = new ArrayList<>();

        if (capacityByPeriod.stream().anyMatch(PeriodCapacityDto::overAllocated)) {
            flags.add(new RiskFlagDto(
                RiskFlagDto.FlagType.OVER_ALLOCATION, RiskFlagDto.Severity.RED,
                "Team over-allocated in one or more periods",
                "Team", id.toString()
            ));
        }

        for (Epic epic : allEpics) {
            for (SkillShortfallDto sf : derivation.skillShortfalls(epic, members)) {
                flags.add(new RiskFlagDto(
                    RiskFlagDto.FlagType.SKILL_SHORTFALL, RiskFlagDto.Severity.RED,
                    String.format("'%s' needs %.1fpd more %s", epic.getName(), sf.gapPd(), sf.skillName()),
                    "Epic", epic.getId().toString()
                ));
            }
        }

        for (Person p : members) {
            if (derivation.isCriticalResource(p, members, allEpics, to)) {
                flags.add(new RiskFlagDto(
                    RiskFlagDto.FlagType.CRITICAL_RESOURCE, RiskFlagDto.Severity.AMBER,
                    p.getName() + " is the sole holder of a required skill",
                    "Person", p.getId().toString()
                ));
            }
        }

        return new TeamSummaryDto(
            team.getId(), team.getName(),
            team.getOverheadFactor(), team.getSupportFactor(),
            members.size(), capacityByPeriod, flags
        );
    }

    @Transactional
    public Team createTeam(TeamRequest req) {
        Team team = new Team();
        team.setName(req.name());
        if (req.overheadFactor() != null) team.setOverheadFactor(req.overheadFactor());
        if (req.supportFactor()  != null) team.setSupportFactor(req.supportFactor());
        return teamRepository.save(team);
    }

    @Transactional
    public Team updateTeam(UUID id, TeamRequest req) {
        Team team = teamRepository.findById(id).orElseThrow();
        if (req.name()           != null) team.setName(req.name());
        if (req.overheadFactor() != null) team.setOverheadFactor(req.overheadFactor());
        if (req.supportFactor()  != null) team.setSupportFactor(req.supportFactor());
        return teamRepository.save(team);
    }

    // ── People ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PersonDto> getPersons(UUID teamId) {
        List<Person> people = teamId != null
            ? personRepository.findByTeamId(teamId)
            : personRepository.findAll();
        return people.stream().map(this::toPersonDto).toList();
    }

    @Transactional(readOnly = true)
    public PersonDto getPerson(UUID id) {
        return toPersonDto(personRepository.findById(id).orElseThrow());
    }

    @Transactional
    public PersonDto createPerson(PersonRequest req) {
        Team team = teamRepository.findById(req.teamId()).orElseThrow();
        Person person = new Person();
        person.setName(req.name());
        person.setTeam(team);
        if (req.baseAvailability() != null) person.setBaseAvailability(req.baseAvailability());
        if (req.skills()           != null) person.setSkills(resolvePersonSkills(req.skills()));
        return toPersonDto(personRepository.save(person));
    }

    @Transactional
    public PersonDto updatePerson(UUID id, PersonRequest req) {
        Person person = personRepository.findById(id).orElseThrow();
        if (req.name()             != null) person.setName(req.name());
        if (req.teamId()           != null) person.setTeam(teamRepository.findById(req.teamId()).orElseThrow());
        if (req.baseAvailability() != null) person.setBaseAvailability(req.baseAvailability());
        if (req.skills()           != null) person.setSkills(resolvePersonSkills(req.skills()));
        return toPersonDto(personRepository.save(person));
    }

    @Transactional
    public PersonDto addAvailabilityOverride(UUID personId, AvailabilityOverride override) {
        Person person = personRepository.findById(personId).orElseThrow();
        person.getAvailabilityOverrides().add(override);
        return toPersonDto(personRepository.save(person));
    }

    @Transactional
    public PersonDto removeAvailabilityOverride(UUID personId, int index) {
        Person person = personRepository.findById(personId).orElseThrow();
        person.getAvailabilityOverrides().remove(index);
        return toPersonDto(personRepository.save(person));
    }

    // ── Initiatives ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InitiativeSummaryDto> getInitiativeSummaries() {
        return initiativeRepository.findByOrderByPriorityAsc().stream()
            .map(i -> getInitiativeSummary(i.getId()))
            .toList();
    }

    @Transactional(readOnly = true)
    public InitiativeSummaryDto getInitiativeSummary(UUID id) {
        Initiative initiative = initiativeRepository.findById(id).orElseThrow();
        List<Epic> epics = epicRepository.findByInitiativeId(id);

        double bottomUp = derivation.bottomUpEstimate(epics);
        double gap      = derivation.decompositionGap(initiative, epics);

        List<UUID> teamsInvolved = epics.stream()
            .map(e -> e.getTeam().getId())
            .distinct()
            .toList();

        List<RiskFlagDto> flags = new ArrayList<>();
        if (gap > 0) {
            flags.add(new RiskFlagDto(
                RiskFlagDto.FlagType.DECOMPOSITION_GAP, RiskFlagDto.Severity.AMBER,
                String.format("%.1fpd not yet decomposed into epics", gap),
                "Initiative", id.toString()
            ));
        }

        return new InitiativeSummaryDto(
            initiative.getId(), initiative.getName(),
            initiative.getStatus(), initiative.getPriority(),
            initiative.getTopDownEstimate(), bottomUp, gap,
            teamsInvolved, flags
        );
    }

    @Transactional
    public Initiative createInitiative(InitiativeRequest req) {
        Initiative initiative = new Initiative();
        initiative.setName(req.name());
        initiative.setTopDownEstimate(req.topDownEstimate());
        initiative.setTargetDeliveryDate(req.targetDeliveryDate());
        if (req.description()        != null) initiative.setDescription(req.description());
        if (req.startDate()          != null) initiative.setStartDate(req.startDate());
        if (req.priority()           != null) initiative.setPriority(req.priority());
        if (req.ownerName()          != null) initiative.setOwnerName(req.ownerName());
        if (req.status()             != null) initiative.setStatus(Initiative.Status.valueOf(req.status()));
        return initiativeRepository.save(initiative);
    }

    @Transactional
    public Initiative updateInitiative(UUID id, InitiativeRequest req) {
        Initiative init = initiativeRepository.findById(id).orElseThrow();
        if (req.name()               != null) init.setName(req.name());
        if (req.description()        != null) init.setDescription(req.description());
        if (req.topDownEstimate()    != null) init.setTopDownEstimate(req.topDownEstimate());
        if (req.startDate()          != null) init.setStartDate(req.startDate());
        if (req.targetDeliveryDate() != null) init.setTargetDeliveryDate(req.targetDeliveryDate());
        if (req.priority()           != null) init.setPriority(req.priority());
        if (req.ownerName()          != null) init.setOwnerName(req.ownerName());
        if (req.status()             != null) init.setStatus(Initiative.Status.valueOf(req.status()));
        return initiativeRepository.save(init);
    }

    // ── Epics ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EpicSummaryDto> getEpicSummaries(UUID teamId, UUID initiativeId) {
        List<Epic> epics;
        if (teamId != null && initiativeId != null) {
            epics = epicRepository.findByTeamId(teamId).stream()
                .filter(e -> e.getInitiative() != null
                          && e.getInitiative().getId().equals(initiativeId))
                .toList();
        } else if (teamId != null) {
            epics = epicRepository.findByTeamId(teamId);
        } else if (initiativeId != null) {
            epics = epicRepository.findByInitiativeId(initiativeId);
        } else {
            epics = epicRepository.findAll();
        }
        return epics.stream().map(this::toEpicSummaryDto).toList();
    }

    @Transactional(readOnly = true)
    public EpicSummaryDto getEpicSummary(UUID id) {
        return toEpicSummaryDto(epicRepository.findById(id).orElseThrow());
    }

    @Transactional
    public EpicSummaryDto createEpic(EpicRequest req) {
        Team team = teamRepository.findById(req.teamId()).orElseThrow();
        Epic epic = new Epic();
        epic.setName(req.name());
        epic.setTeam(team);
        epic.setEstimate(req.estimate());
        epic.setStartDate(req.startDate());
        epic.setDueDate(req.dueDate());
        if (req.initiativeId()  != null) epic.setInitiative(initiativeRepository.findById(req.initiativeId()).orElseThrow());
        if (req.priority()      != null) epic.setPriority(Epic.Priority.valueOf(req.priority()));
        if (req.status()        != null) epic.setStatus(Epic.Status.valueOf(req.status()));
        if (req.requiredSkills() != null) epic.setRequiredSkills(resolveSkillRequirements(req.requiredSkills()));
        return toEpicSummaryDto(epicRepository.save(epic));
    }

    @Transactional
    public EpicSummaryDto updateEpic(UUID id, EpicRequest req) {
        Epic epic = epicRepository.findById(id).orElseThrow();
        if (req.name()          != null) epic.setName(req.name());
        if (req.teamId()        != null) epic.setTeam(teamRepository.findById(req.teamId()).orElseThrow());
        if (req.initiativeId()  != null) epic.setInitiative(initiativeRepository.findById(req.initiativeId()).orElseThrow());
        if (req.estimate()      != null) epic.setEstimate(req.estimate());
        if (req.startDate()     != null) epic.setStartDate(req.startDate());
        if (req.dueDate()       != null) epic.setDueDate(req.dueDate());
        if (req.priority()      != null) epic.setPriority(Epic.Priority.valueOf(req.priority()));
        if (req.status()        != null) epic.setStatus(Epic.Status.valueOf(req.status()));
        if (req.requiredSkills() != null) epic.setRequiredSkills(resolveSkillRequirements(req.requiredSkills()));
        return toEpicSummaryDto(epicRepository.save(epic));
    }

    @Transactional
    public void deleteEpic(UUID id) {
        epicRepository.deleteById(id);
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /**
     * Preview applies deltas to in-memory state only — no DB writes.
     *
     * Strategy: preload all team data into maps BEFORE any delta is applied;
     * those maps hold references into the Hibernate L1 cache. Deltas modify
     * those same cached objects (readOnly tx = FlushMode.NEVER, so nothing
     * is written to DB). The second pass over the same maps then sees the
     * in-memory modifications.
     */
    @Transactional(readOnly = true)
    public ScenarioImpactDto previewScenario(ScenarioPreviewRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusMonths(12);

        Set<UUID> affectedTeamIds = resolveAffectedTeams(request.deltas());

        Map<UUID, Team>         teamMap    = new HashMap<>();
        Map<UUID, List<Person>> membersMap = new HashMap<>();
        Map<UUID, List<Epic>>   epicsMap   = new HashMap<>();
        for (UUID tid : affectedTeamIds) {
            teamMap.put(tid, teamRepository.findById(tid).orElseThrow());
            membersMap.put(tid, personRepository.findByTeamId(tid));
            epicsMap.put(tid, epicRepository.findByTeamId(tid));
        }

        Map<UUID, List<RiskFlagDto>> baselineFlags = new HashMap<>();
        Map<UUID, Double>            baselineLoads = new HashMap<>();
        for (UUID tid : affectedTeamIds) {
            baselineFlags.put(tid, computeTeamRiskFlags(
                teamMap.get(tid), membersMap.get(tid), epicsMap.get(tid), today, horizon));
            baselineLoads.put(tid, derivation.committedLoad(epicsMap.get(tid), today, horizon));
        }

        for (DeltaRequest d : request.deltas()) {
            applyDeltaField(d);
        }

        Map<UUID, List<RiskFlagDto>> scenarioFlags = new HashMap<>();
        Map<UUID, Double>            scenarioLoads = new HashMap<>();
        for (UUID tid : affectedTeamIds) {
            scenarioFlags.put(tid, computeTeamRiskFlags(
                teamMap.get(tid), membersMap.get(tid), epicsMap.get(tid), today, horizon));
            scenarioLoads.put(tid, derivation.committedLoad(epicsMap.get(tid), today, horizon));
        }

        Set<String> baselineKeys = baselineFlags.values().stream()
            .flatMap(Collection::stream).map(this::flagKey).collect(Collectors.toSet());
        Set<String> scenarioKeys = scenarioFlags.values().stream()
            .flatMap(Collection::stream).map(this::flagKey).collect(Collectors.toSet());

        List<RiskFlagDto> newFlags = scenarioFlags.values().stream()
            .flatMap(Collection::stream)
            .filter(f -> !baselineKeys.contains(flagKey(f)))
            .toList();
        List<RiskFlagDto> resolvedFlags = baselineFlags.values().stream()
            .flatMap(Collection::stream)
            .filter(f -> !scenarioKeys.contains(flagKey(f)))
            .toList();

        List<TeamCapacityDeltaDto> capacityDeltas = affectedTeamIds.stream()
            .map(tid -> {
                Team t = teamMap.get(tid);
                double bLoad = baselineLoads.get(tid);
                double sLoad = scenarioLoads.get(tid);
                return new TeamCapacityDeltaDto(
                    tid, t.getName(), today, horizon, bLoad, sLoad, sLoad - bLoad);
            })
            .toList();

        return new ScenarioImpactDto(UUID.randomUUID(), newFlags, resolvedFlags, capacityDeltas);
    }

    @Transactional
    public ChangeSetDto commitScenario(ScenarioCommitRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("Commit reason must not be blank");
        }

        String actor = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .map(a -> a.getName())
            .orElse("anonymous");
        ChangeSet.Source source = ChangeSet.Source.valueOf(request.source());

        List<LoggedDelta> loggedDeltas = new ArrayList<>();
        for (DeltaRequest d : request.deltas()) {
            String oldValue = readCurrentFieldValue(d);
            applyAndSaveEntity(d);
            loggedDeltas.add(new LoggedDelta(
                d.entityType(), d.entityId(), d.field(),
                oldValue,
                d.newValue() != null ? d.newValue().toString() : null
            ));
        }

        ChangeSet changeSet = ChangeSet.builder()
            .actor(actor)
            .source(source)
            .reason(request.reason())
            .changes(loggedDeltas)
            .build();

        ChangeSet saved = changeSetRepository.save(changeSet);
        return new ChangeSetDto(
            saved.getId(), saved.getTimestamp().toString(),
            saved.getActor(), saved.getSource(), saved.getReason(),
            saved.getChanges().size()
        );
    }

    // ── Change log ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeSetDto> getChangeLog() {
        return changeSetRepository.findByOrderByTimestampDesc().stream()
            .map(cs -> new ChangeSetDto(
                cs.getId(), cs.getTimestamp().toString(),
                cs.getActor(), cs.getSource(), cs.getReason(),
                cs.getChanges().size()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeSetDto> getChangeLogForEntity(String entityType, String entityId) {
        return changeSetRepository.findByAffectedEntity(entityType, entityId).stream()
            .map(cs -> new ChangeSetDto(
                cs.getId(), cs.getTimestamp().toString(),
                cs.getActor(), cs.getSource(), cs.getReason(),
                cs.getChanges().size()))
            .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PersonDto toPersonDto(Person person) {
        List<PersonDto.PersonSkillDto> skills = person.getSkills().stream()
            .map(ps -> new PersonDto.PersonSkillDto(ps.getSkill(), ps.getProficiency().toString()))
            .toList();
        return new PersonDto(
            person.getId(),
            person.getName(),
            person.getTeam().getId(),
            person.getBaseAvailability(),
            skills,
            person.getAvailabilityOverrides()
        );
    }

    private List<PersonSkill> resolvePersonSkills(List<PersonRequest.PersonSkillRequest> requests) {
        return requests.stream()
            .map(sr -> {
                Skill skill = skillRepository.findById(sr.skillId()).orElseThrow(
                    () -> new IllegalArgumentException("Skill not found: " + sr.skillId()));
                PersonSkill ps = new PersonSkill();
                ps.setSkill(skill);
                ps.setProficiency(Proficiency.valueOf(sr.proficiency()));
                return ps;
            })
            .toList();
    }

    private List<EpicSkillRequirement> resolveSkillRequirements(List<EpicRequest.SkillRequirementRequest> requests) {
        return requests.stream()
            .map(sr -> {
                Skill skill = skillRepository.findById(sr.skillId()).orElseThrow(
                    () -> new IllegalArgumentException("Skill not found: " + sr.skillId()));
                EpicSkillRequirement req = new EpicSkillRequirement();
                req.setSkill(skill);
                req.setMinProficiency(Proficiency.valueOf(sr.minProficiency()));
                req.setDemandPd(sr.demandPd());
                return req;
            })
            .toList();
    }

    private EpicSummaryDto toEpicSummaryDto(Epic epic) {
        List<Person> members = personRepository.findByTeamId(epic.getTeam().getId());
        List<SkillShortfallDto> shortfalls = derivation.skillShortfalls(epic, members);
        boolean overAllocated = derivation.isOverAllocated(
            epic.getTeam(), members, List.of(epic), epic.getStartDate(), epic.getDueDate());
        return new EpicSummaryDto(
            epic.getId(), epic.getName(),
            epic.getTeam().getId(),
            epic.getInitiative() != null ? epic.getInitiative().getId() : null,
            epic.getPriority(), epic.getStatus(),
            epic.getEstimate(), epic.getStartDate(), epic.getDueDate(),
            shortfalls, !shortfalls.isEmpty() || overAllocated
        );
    }

    private List<RiskFlagDto> computeTeamRiskFlags(Team team, List<Person> members,
                                                   List<Epic> epics,
                                                   LocalDate from, LocalDate to) {
        List<RiskFlagDto> flags = new ArrayList<>();

        if (derivation.isOverAllocated(team, members, epics, from, to)) {
            flags.add(new RiskFlagDto(
                RiskFlagDto.FlagType.OVER_ALLOCATION, RiskFlagDto.Severity.RED,
                "Team over-allocated in planning horizon",
                "Team", team.getId().toString()
            ));
        }

        List<Epic> active = epics.stream()
            .filter(e -> e.getStatus() == Epic.Status.COMMITTED || e.getStatus() == Epic.Status.AT_RISK)
            .toList();

        for (Epic epic : active) {
            for (SkillShortfallDto sf : derivation.skillShortfalls(epic, members)) {
                flags.add(new RiskFlagDto(
                    RiskFlagDto.FlagType.SKILL_SHORTFALL, RiskFlagDto.Severity.RED,
                    String.format("'%s' needs %.1fpd more %s", epic.getName(), sf.gapPd(), sf.skillName()),
                    "Epic", epic.getId().toString()
                ));
            }
        }

        for (Person p : members) {
            if (derivation.isCriticalResource(p, members, active, to)) {
                flags.add(new RiskFlagDto(
                    RiskFlagDto.FlagType.CRITICAL_RESOURCE, RiskFlagDto.Severity.AMBER,
                    p.getName() + " is sole holder of a required skill",
                    "Person", p.getId().toString()
                ));
            }
        }

        return flags;
    }

    private Set<UUID> resolveAffectedTeams(List<DeltaRequest> deltas) {
        Set<UUID> teamIds = new HashSet<>();
        for (DeltaRequest d : deltas) {
            UUID id = UUID.fromString(d.entityId());
            switch (d.entityType()) {
                case "Epic"       -> epicRepository.findById(id)
                    .ifPresent(e -> teamIds.add(e.getTeam().getId()));
                case "Person"     -> personRepository.findById(id)
                    .ifPresent(p -> teamIds.add(p.getTeam().getId()));
                case "Team"       -> teamIds.add(id);
                case "Initiative" -> epicRepository.findByInitiativeId(id).stream()
                    .map(e -> e.getTeam().getId())
                    .forEach(teamIds::add);
            }
        }
        return teamIds;
    }

    private void applyDeltaField(DeltaRequest d) {
        UUID id = UUID.fromString(d.entityId());
        String v = d.newValue() != null ? d.newValue().toString() : null;
        switch (d.entityType()) {
            case "Epic"       -> epicRepository.findById(id).ifPresent(e -> applyEpicField(e, d.field(), v));
            case "Person"     -> personRepository.findById(id).ifPresent(p -> applyPersonField(p, d.field(), v));
            case "Team"       -> teamRepository.findById(id).ifPresent(t -> applyTeamField(t, d.field(), v));
            case "Initiative" -> initiativeRepository.findById(id)
                .ifPresent(i -> applyInitiativeField(i, d.field(), v));
        }
    }

    private void applyAndSaveEntity(DeltaRequest d) {
        UUID id = UUID.fromString(d.entityId());
        String v = d.newValue() != null ? d.newValue().toString() : null;
        switch (d.entityType()) {
            case "Epic" -> {
                Epic e = epicRepository.findById(id).orElseThrow();
                applyEpicField(e, d.field(), v);
                epicRepository.save(e);
            }
            case "Person" -> {
                Person p = personRepository.findById(id).orElseThrow();
                applyPersonField(p, d.field(), v);
                personRepository.save(p);
            }
            case "Team" -> {
                Team t = teamRepository.findById(id).orElseThrow();
                applyTeamField(t, d.field(), v);
                teamRepository.save(t);
            }
            case "Initiative" -> {
                Initiative i = initiativeRepository.findById(id).orElseThrow();
                applyInitiativeField(i, d.field(), v);
                initiativeRepository.save(i);
            }
        }
    }

    private String readCurrentFieldValue(DeltaRequest d) {
        UUID id = UUID.fromString(d.entityId());
        return switch (d.entityType()) {
            case "Epic" -> epicRepository.findById(id).map(e -> switch (d.field()) {
                case "status"    -> e.getStatus().toString();
                case "priority"  -> e.getPriority().toString();
                case "estimate"  -> e.getEstimate().toString();
                case "startDate" -> e.getStartDate().toString();
                case "dueDate"   -> e.getDueDate().toString();
                default -> null;
            }).orElse(null);
            case "Person" -> personRepository.findById(id).map(p ->
                "baseAvailability".equals(d.field()) ? p.getBaseAvailability().toString() : null
            ).orElse(null);
            case "Initiative" -> initiativeRepository.findById(id).map(i -> switch (d.field()) {
                case "status"             -> i.getStatus().toString();
                case "topDownEstimate"    -> i.getTopDownEstimate().toString();
                case "targetDeliveryDate" -> i.getTargetDeliveryDate() != null
                    ? i.getTargetDeliveryDate().toString() : null;
                default -> null;
            }).orElse(null);
            case "Team" -> teamRepository.findById(id).map(t -> switch (d.field()) {
                case "overheadFactor" -> t.getOverheadFactor().toString();
                case "supportFactor"  -> t.getSupportFactor().toString();
                default -> null;
            }).orElse(null);
            default -> null;
        };
    }

    private void applyEpicField(Epic e, String field, String value) {
        switch (field) {
            case "status"    -> e.setStatus(Epic.Status.valueOf(value));
            case "priority"  -> e.setPriority(Epic.Priority.valueOf(value));
            case "estimate"  -> e.setEstimate(Double.parseDouble(value));
            case "startDate" -> e.setStartDate(LocalDate.parse(value));
            case "dueDate"   -> e.setDueDate(LocalDate.parse(value));
        }
    }

    private void applyPersonField(Person p, String field, String value) {
        if ("baseAvailability".equals(field)) {
            p.setBaseAvailability(Double.parseDouble(value));
        }
    }

    private void applyTeamField(Team t, String field, String value) {
        switch (field) {
            case "overheadFactor" -> t.setOverheadFactor(Double.parseDouble(value));
            case "supportFactor"  -> t.setSupportFactor(Double.parseDouble(value));
        }
    }

    private void applyInitiativeField(Initiative i, String field, String value) {
        switch (field) {
            case "status"             -> i.setStatus(Initiative.Status.valueOf(value));
            case "topDownEstimate"    -> i.setTopDownEstimate(Double.parseDouble(value));
            case "targetDeliveryDate" -> i.setTargetDeliveryDate(LocalDate.parse(value));
        }
    }

    private String flagKey(RiskFlagDto f) {
        return f.type() + "|" + f.entityType() + "|" + f.entityId();
    }
}
