package com.capacityplanner.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only change log entry.
 *
 * Written when a scenario is committed or any direct edit is saved.
 * Never updated or deleted — @Immutable enforces this at the Hibernate level.
 *
 * source:  CHANGE_REQUEST = top-down (leadership / PM adds or reshapes demand)
 *          TEAM_FEEDBACK  = bottom-up (team re-estimates, reports slip, flags lost capacity)
 */
@Entity
@Immutable
@Table(name = "change_sets")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChangeSet {

    public enum Source { CHANGE_REQUEST, TEAM_FEEDBACK }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Column(nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    /**
     * Required, non-empty.
     * The "why" behind the change — directly addresses the brief's pain
     * of silent cross-team updates that nobody can reconstruct.
     */
    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** The scenario this commit originated from, if any */
    private UUID scenarioId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "change_set_deltas", joinColumns = @JoinColumn(name = "change_set_id"))
    @Builder.Default
    private List<LoggedDelta> changes = new ArrayList<>();
}
