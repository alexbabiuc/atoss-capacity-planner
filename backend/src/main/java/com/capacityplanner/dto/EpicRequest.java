package com.capacityplanner.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EpicRequest(
    String name,
    UUID teamId,
    UUID initiativeId,
    Double estimate,
    LocalDate startDate,
    LocalDate dueDate,
    String priority,
    String status,
    List<SkillRequirementRequest> requiredSkills
) {
    public record SkillRequirementRequest(UUID skillId, String minProficiency, Double demandPd) {}
}
