package com.capacityplanner.entity;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EpicSkillRequirement {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    /** Minimum proficiency level team members must have for this requirement to be satisfied */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Proficiency minProficiency;

    /** Person-days of this skill needed to complete the epic */
    @Column(nullable = false)
    private Double demandPd;
}
