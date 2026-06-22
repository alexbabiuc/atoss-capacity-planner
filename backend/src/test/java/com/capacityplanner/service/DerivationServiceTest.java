package com.capacityplanner.service;

import com.capacityplanner.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
class DerivationServiceTest {

    private final DerivationService service = new DerivationService();

    // ── Working days ──────────────────────────────────────────────────────────

    @Test
    void workingDaysBetween_excludesWeekends() {
        // Mon 2025-01-06 to Fri 2025-01-10 = 5 days
        assertThat(service.workingDaysBetween(
            LocalDate.of(2025, 1, 6),
            LocalDate.of(2025, 1, 10)
        )).isEqualTo(5);
    }

    @Test
    void workingDaysBetween_singleDay_weekday() {
        assertThat(service.workingDaysBetween(
            LocalDate.of(2025, 1, 6),
            LocalDate.of(2025, 1, 6)
        )).isEqualTo(1);
    }

    @Test
    void workingDaysBetween_startAfterEnd_returnsZero() {
        assertThat(service.workingDaysBetween(
            LocalDate.of(2025, 1, 10),
            LocalDate.of(2025, 1, 6)
        )).isEqualTo(0);
    }

    // ── Person availability ───────────────────────────────────────────────────

    @Test
    void effectiveAvailability_noOverride_returnsBase() {
        var person = Person.builder().baseAvailability(0.8).availabilityOverrides(List.of()).build();
        assertThat(service.effectiveAvailabilityOn(person, LocalDate.of(2025, 3, 15)))
            .isEqualTo(0.8);
    }

    @Test
    void effectiveAvailability_withOverride_returnsOverrideFactor() {
        var override = new AvailabilityOverride(
            LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), 0.0, "Leave");
        var person = Person.builder()
            .baseAvailability(1.0)
            .availabilityOverrides(List.of(override))
            .build();
        assertThat(service.effectiveAvailabilityOn(person, LocalDate.of(2025, 3, 15)))
            .isEqualTo(0.0);
    }

    @Test
    void effectiveAvailability_openEndedOverride_appliesPermanently() {
        var override = new AvailabilityOverride(
            LocalDate.of(2025, 6, 1), null, 0.0, "Attrition");
        var person = Person.builder()
            .baseAvailability(1.0)
            .availabilityOverrides(List.of(override))
            .build();
        assertThat(service.effectiveAvailabilityOn(person, LocalDate.of(2025, 12, 31)))
            .isEqualTo(0.0);
    }

    // ── Epic load distribution ────────────────────────────────────────────────

    @Test
    void scheduledLoad_fullOverlap_returnsFullEstimate() {
        var epic = epicWith(20.0,
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        double load = service.scheduledLoad(epic,
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertThat(load).isCloseTo(20.0, within(0.5));
    }

    @Test
    void scheduledLoad_noOverlap_returnsZero() {
        var epic = epicWith(20.0,
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        double load = service.scheduledLoad(epic,
            LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        assertThat(load).isEqualTo(0.0);
    }

    @Test
    void scheduledLoad_halfOverlap_returnsHalfEstimate() {
        // Epic is 22 working days in Jan 2025 (Jan 1–31, but we approximate with 20 days)
        // Period covers only the second half — should return ~50%
        var epic = epicWith(20.0,
            LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 31)); // Mon–Fri, 20 days
        double load = service.scheduledLoad(epic,
            LocalDate.of(2025, 1, 20), LocalDate.of(2025, 1, 31)); // last ~half
        assertThat(load).isGreaterThan(5.0).isLessThan(15.0);
    }

    // ── Over-allocation ───────────────────────────────────────────────────────

    @Test
    void isOverAllocated_whenLoadExceedsCapacity_returnsTrue() {
        var team = Team.builder().overheadFactor(0.15).supportFactor(0.10).build();
        // One full-time person for Jan 2025 → ~23 working days × 0.75 = ~17 PD effective
        var person = Person.builder().baseAvailability(1.0).availabilityOverrides(List.of()).build();
        // Epic needs 30 PD — over capacity
        var epic = epicWith(30.0, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        epic.setStatus(Epic.Status.COMMITTED);

        assertThat(service.isOverAllocated(team, List.of(person), List.of(epic),
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)
        )).isTrue();
    }

    @Test
    void isOverAllocated_whenLoadUnderCapacity_returnsFalse() {
        var team = Team.builder().overheadFactor(0.15).supportFactor(0.10).build();
        var person = Person.builder().baseAvailability(1.0).availabilityOverrides(List.of()).build();
        // Epic needs only 5 PD — well under capacity
        var epic = epicWith(5.0, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        epic.setStatus(Epic.Status.COMMITTED);

        assertThat(service.isOverAllocated(team, List.of(person), List.of(epic),
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)
        )).isFalse();
    }

    // ── Decomposition gap ─────────────────────────────────────────────────────

    @Test
    void decompositionGap_underDecomposed_returnsPositive() {
        var init = Initiative.builder().topDownEstimate(100.0).build();
        var e1 = epicWith(30.0, LocalDate.now(), LocalDate.now().plusMonths(1));
        var e2 = epicWith(40.0, LocalDate.now(), LocalDate.now().plusMonths(1));
        assertThat(service.decompositionGap(init, List.of(e1, e2))).isCloseTo(30.0, within(0.1));
    }

    @Test
    void decompositionGap_fullyDecomposed_returnsZeroOrNegative() {
        var init = Initiative.builder().topDownEstimate(70.0).build();
        var e1 = epicWith(40.0, LocalDate.now(), LocalDate.now().plusMonths(1));
        var e2 = epicWith(35.0, LocalDate.now(), LocalDate.now().plusMonths(1));
        assertThat(service.decompositionGap(init, List.of(e1, e2))).isLessThanOrEqualTo(0.0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Epic epicWith(double estimate, LocalDate start, LocalDate end) {
        return Epic.builder()
            .estimate(estimate)
            .startDate(start)
            .dueDate(end)
            .status(Epic.Status.COMMITTED)
            .requiredSkills(List.of())
            .build();
    }
}
