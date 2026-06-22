package com.capacityplanner.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Fraction of raw capacity consumed by recurring overhead (ceremonies, 1:1s).
     * Default 0.15 — see application.yml.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    @Column(nullable = false)
    @Builder.Default
    private Double overheadFactor = 0.15;

    /**
     * Fraction consumed by support / on-call / reactive interrupts.
     * Default 0.10.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    @Column(nullable = false)
    @Builder.Default
    private Double supportFactor = 0.10;

    // Derived fields (effectiveCapacity, capacityGap, etc.) are computed by
    // DerivationService and returned in response DTOs — never persisted.
}
