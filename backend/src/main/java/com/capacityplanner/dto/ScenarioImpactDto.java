package com.capacityplanner.dto;

import java.util.List;
import java.util.UUID;

public record ScenarioImpactDto(
    UUID scenarioId,
    List<RiskFlagDto> newFlags,
    List<RiskFlagDto> resolvedFlags,
    List<TeamCapacityDeltaDto> capacityDeltas
) {}
