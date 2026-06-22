package com.capacityplanner.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TeamCapacityDeltaDto(
    UUID teamId,
    String teamName,
    LocalDate periodStart,
    LocalDate periodEnd,
    double baselineLoadPd,
    double scenarioLoadPd,
    double deltaLoadPd
) {}
