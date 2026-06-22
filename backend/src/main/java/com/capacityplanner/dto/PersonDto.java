package com.capacityplanner.dto;

import com.capacityplanner.entity.AvailabilityOverride;
import com.capacityplanner.entity.Skill;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of a Person. Uses teamId (UUID) rather than a nested Team object
 * to avoid serializing lazy Hibernate proxies.
 */
public record PersonDto(
    UUID id,
    String name,
    UUID teamId,
    Double baseAvailability,
    List<PersonSkillDto> skills,
    List<AvailabilityOverride> availabilityOverrides
) {
    public record PersonSkillDto(Skill skill, String proficiency) {}
}
