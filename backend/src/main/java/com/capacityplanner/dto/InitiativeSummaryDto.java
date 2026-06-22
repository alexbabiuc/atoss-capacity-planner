package com.capacityplanner.dto;

import com.capacityplanner.entity.Initiative;
import java.util.List;
import java.util.UUID;

public record InitiativeSummaryDto(
    UUID id,
    String name,
    Initiative.Status status,
    int priority,
    double topDownEstimatePd,
    double bottomUpEstimatePd,
    double decompositionGapPd,
    List<UUID> teamsInvolved,
    List<RiskFlagDto> flags
) {}
