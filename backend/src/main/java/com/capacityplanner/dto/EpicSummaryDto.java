package com.capacityplanner.dto;

import com.capacityplanner.entity.Epic;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EpicSummaryDto(
    UUID id,
    String name,
    UUID teamId,
    UUID initiativeId,
    Epic.Priority priority,
    Epic.Status status,
    double estimatePd,
    LocalDate startDate,
    LocalDate dueDate,
    List<SkillShortfallDto> skillShortfalls,
    boolean isAtRisk
) {}
