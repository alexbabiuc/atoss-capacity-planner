package com.capacityplanner.dto;

import java.util.List;
import java.util.UUID;

public record PersonRequest(
    String name,
    UUID teamId,
    Double baseAvailability,
    List<PersonSkillRequest> skills
) {
    public record PersonSkillRequest(UUID skillId, String proficiency) {}
}
