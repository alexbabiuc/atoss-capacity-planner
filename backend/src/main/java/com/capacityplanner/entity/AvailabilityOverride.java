package com.capacityplanner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.time.LocalDate;

/**
 * Time-boxed deviation from a person's base availability.
 *
 * factor 0.0 = fully unavailable (leave, attrition)
 * factor 0.5 = half-time for the period
 * Open endDate (null) = permanent change (e.g. attrition, role change)
 *
 * This same structure is the input to what-if scenarios — applying an override
 * with factor=0 from a future date IS the "person leaves" scenario.
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AvailabilityOverride {

    @Column(name = "override_start", nullable = false)
    private LocalDate startDate;

    /** Null = open-ended (permanent change) */
    @Column(name = "override_end")
    private LocalDate endDate;

    /**
     * Multiplicative factor applied to baseAvailability for the period.
     * 0.0–1.0. E.g. 0.0 = fully out, 0.5 = half available.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    @Column(name = "override_factor", nullable = false)
    private Double factor;

    /** Human-readable reason required — feeds the change log. */
    @Column(name = "override_reason", nullable = false)
    private String reason;
}
