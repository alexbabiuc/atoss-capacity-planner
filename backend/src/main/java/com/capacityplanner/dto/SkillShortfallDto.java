package com.capacityplanner.dto;

import com.capacityplanner.entity.Proficiency;
import java.util.UUID;

public record SkillShortfallDto(
    UUID skillId,
    String skillName,
    Proficiency minProficiency,
    double demandPd,
    double availablePd,
    double gapPd
) {}
