package com.capacityplanner.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "initiatives")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Initiative {

    public enum Status { PROPOSED, COMMITTED, AT_RISK, DELIVERED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Top-down strategic estimate in person-days.
     * Independent from the sum of epic estimates (Decision 1).
     * The gap between this and bottomUpEstimate (derived) is the decomposition gap.
     */
    @Column(nullable = false)
    private Double topDownEstimate;

    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate targetDeliveryDate;

    /** Portfolio rank — lower number = higher priority */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PROPOSED;

    // bottomUpEstimate, decompositionGap, teamsInvolved, aggregateStatus
    // are all derived by DerivationService — never persisted.
}
