package com.capacityplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "epics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Epic {

    public enum Priority { MUST, SHOULD, COULD, WONT }
    public enum Status { COMMITTED, AT_RISK, DEFERRED, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nullable — orphan epics model BAU / unplanned work */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiative_id")
    private Initiative initiative;

    /** An epic belongs to exactly one team */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Bottom-up estimate in person-days */
    @Column(nullable = false)
    private Double estimate;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    /**
     * MoSCoW priority.
     * WONT is the explicit "undeliverable / deferred" state used when
     * a capacity gap forces a deprioritisation decision.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Priority priority = Priority.SHOULD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.COMMITTED;

    /**
     * Optional skill requirements. Each declares the skill, the minimum
     * proficiency required, and the demand in person-days.
     * Stored as an element collection in epic_required_skills.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "epic_required_skills", joinColumns = @JoinColumn(name = "epic_id"))
    @Builder.Default
    private List<EpicSkillRequirement> requiredSkills = new ArrayList<>();

    // scheduledLoad(period), skillShortfall, isAtRisk — derived by DerivationService.
}
