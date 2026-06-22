package com.capacityplanner.service;

import com.capacityplanner.dto.SkillShortfallDto;
import com.capacityplanner.entity.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure computation engine — all derived fields from the schema live here.
 *
 * CONTRACT: this service NEVER writes to the database.
 * All inputs are stored entities; all outputs are DTO projections.
 * This makes the derivation testable in isolation and guarantees
 * that views can never disagree (no stale cached column to drift).
 */
@Service
public class DerivationService {

    // ── Working-day helpers ───────────────────────────────────────────────────

    /**
     * Count working days (Mon–Fri) in [start, end] inclusive.
     * TODO: inject a holiday calendar for more accurate results.
     */
    public long workingDaysBetween(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) return 0;
        return start.datesUntil(end.plusDays(1))
            .filter(d -> d.getDayOfWeek().getValue() <= 5)
            .count();
    }

    // ── Person capacity ───────────────────────────────────────────────────────

    /**
     * Effective availability fraction for a person on a specific day.
     * Applies the first matching override (ordered by startDate ascending).
     */
    public double effectiveAvailabilityOn(Person person, LocalDate day) {
        return person.getAvailabilityOverrides().stream()
            .filter(o -> !day.isBefore(o.getStartDate())
                      && (o.getEndDate() == null || !day.isAfter(o.getEndDate())))
            .mapToDouble(AvailabilityOverride::getFactor)
            .findFirst()
            .orElse(person.getBaseAvailability());
    }

    /**
     * Total available person-days for a person in [periodStart, periodEnd].
     */
    public double totalAvailablePd(Person person, LocalDate periodStart, LocalDate periodEnd) {
        return periodStart.datesUntil(periodEnd.plusDays(1))
            .filter(d -> d.getDayOfWeek().getValue() <= 5)
            .mapToDouble(d -> effectiveAvailabilityOn(person, d))
            .sum();
    }

    // ── Team capacity ─────────────────────────────────────────────────────────

    public double rawCapacity(List<Person> members, LocalDate start, LocalDate end) {
        return members.stream()
            .mapToDouble(p -> totalAvailablePd(p, start, end))
            .sum();
    }

    public double effectiveCapacity(Team team, List<Person> members, LocalDate start, LocalDate end) {
        double raw = rawCapacity(members, start, end);
        return raw * (1.0 - team.getOverheadFactor() - team.getSupportFactor());
    }

    /**
     * Skilled capacity: person-days available from members who hold a skill
     * at or above the required proficiency.
     */
    public double skilledCapacity(List<Person> members, UUID skillId,
                                  Proficiency minProficiency,
                                  LocalDate start, LocalDate end) {
        return members.stream()
            .filter(p -> p.getSkills().stream()
                .anyMatch(ps -> ps.getSkill().getId().equals(skillId)
                             && ps.getProficiency().ordinal() >= minProficiency.ordinal()))
            .mapToDouble(p -> totalAvailablePd(p, start, end))
            .sum();
    }

    // ── Epic load ─────────────────────────────────────────────────────────────

    /**
     * Person-days this epic consumes within [periodStart, periodEnd].
     * Default: even distribution across working days in the epic's own range.
     *
     * Assumption (flagged in design doc): even spread.
     * Future: stored Allocation overrides per period.
     */
    public double scheduledLoad(Epic epic, LocalDate periodStart, LocalDate periodEnd) {
        LocalDate epicStart = epic.getStartDate();
        LocalDate epicEnd   = epic.getDueDate();

        // Intersect epic window with the query period
        LocalDate overlapStart = epicStart.isAfter(periodStart) ? epicStart : periodStart;
        LocalDate overlapEnd   = epicEnd.isBefore(periodEnd)    ? epicEnd   : periodEnd;

        if (overlapStart.isAfter(overlapEnd)) return 0.0;

        long epicWorkingDays   = workingDaysBetween(epicStart, epicEnd);
        if (epicWorkingDays == 0) return 0.0;

        long overlapWorkingDays = workingDaysBetween(overlapStart, overlapEnd);
        return epic.getEstimate() * ((double) overlapWorkingDays / epicWorkingDays);
    }

    public double committedLoad(List<Epic> epics, LocalDate periodStart, LocalDate periodEnd) {
        return epics.stream()
            .filter(e -> e.getStatus() == Epic.Status.COMMITTED || e.getStatus() == Epic.Status.AT_RISK)
            .mapToDouble(e -> scheduledLoad(e, periodStart, periodEnd))
            .sum();
    }

    // ── Risk flags ────────────────────────────────────────────────────────────

    /** RED: team demand exceeds effective capacity in the period */
    public boolean isOverAllocated(Team team, List<Person> members, List<Epic> epics,
                                   LocalDate start, LocalDate end) {
        return committedLoad(epics, start, end) > effectiveCapacity(team, members, start, end);
    }

    /**
     * RED: an epic requires a skill the team cannot field at the required proficiency
     * for the required person-days within its window.
     */
    public List<SkillShortfallDto> skillShortfalls(Epic epic, List<Person> members) {
        return epic.getRequiredSkills().stream()
            .map(req -> {
                double available = skilledCapacity(members, req.getSkill().getId(),
                    req.getMinProficiency(), epic.getStartDate(), epic.getDueDate());
                double gap = req.getDemandPd() - available;
                return gap > 0
                    ? new SkillShortfallDto(req.getSkill().getId(), req.getSkill().getName(),
                        req.getMinProficiency(), req.getDemandPd(), available, gap)
                    : null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * AMBER: person holds a skill required by committed epics,
     * and they are the only available holder (bus-factor = 1).
     */
    public boolean isCriticalResource(Person person, List<Person> allTeamMembers,
                                      List<Epic> committedEpics, LocalDate horizonEnd) {
        LocalDate today = LocalDate.now();
        return person.getSkills().stream().anyMatch(ps -> {
            boolean epicNeedsIt = committedEpics.stream()
                .filter(e -> e.getStatus() == Epic.Status.COMMITTED)
                .anyMatch(e -> e.getRequiredSkills().stream()
                    .anyMatch(r -> r.getSkill().getId().equals(ps.getSkill().getId())
                               && r.getMinProficiency().ordinal() <= ps.getProficiency().ordinal()));
            if (!epicNeedsIt) return false;

            long availableHolders = allTeamMembers.stream()
                .filter(m -> m.getSkills().stream()
                    .anyMatch(ms -> ms.getSkill().getId().equals(ps.getSkill().getId())
                               && ms.getProficiency().ordinal() >= ps.getProficiency().ordinal()))
                .filter(m -> totalAvailablePd(m, today, horizonEnd) > 0)
                .count();
            return availableHolders <= 1;
        });
    }

    // ── Initiative gaps ───────────────────────────────────────────────────────

    public double bottomUpEstimate(List<Epic> epics) {
        return epics.stream().mapToDouble(Epic::getEstimate).sum();
    }

    /** AMBER: top-down estimate exceeds sum of epics — scope not fully decomposed */
    public double decompositionGap(Initiative initiative, List<Epic> epics) {
        return initiative.getTopDownEstimate() - bottomUpEstimate(epics);
    }
}
