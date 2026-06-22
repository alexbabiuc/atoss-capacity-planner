package com.capacityplanner.dto;

import java.util.List;
import java.util.UUID;

public record TeamSummaryDto(
    UUID id,
    String name,
    double overheadFactor,
    double supportFactor,
    int memberCount,
    List<PeriodCapacityDto> capacityByPeriod,
    List<RiskFlagDto> flags
) {}
