package com.capacityplanner.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "people")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /**
     * FTE fraction. 1.0 = full-time, 0.5 = half-time.
     * The baseline from which availability overrides are applied.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    @Column(nullable = false)
    @Builder.Default
    private Double baseAvailability = 1.0;

    /**
     * Skills with proficiency levels.
     * Stored as an element collection — simple join table people_skills.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "person_skills", joinColumns = @JoinColumn(name = "person_id"))
    @Builder.Default
    private List<PersonSkill> skills = new ArrayList<>();

    /**
     * Time-boxed availability deviations.
     * Covers PTO, parental leave, ramp-up, attrition.
     * Also the input to what-if scenarios ("what if this person leaves in March").
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "availability_overrides", joinColumns = @JoinColumn(name = "person_id"))
    @OrderBy("override_start ASC")
    @Builder.Default
    private List<AvailabilityOverride> availabilityOverrides = new ArrayList<>();
}
