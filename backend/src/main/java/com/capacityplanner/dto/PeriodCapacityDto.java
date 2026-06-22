package com.capacityplanner.dto;

import java.time.LocalDate;

public record PeriodCapacityDto(
    LocalDate periodStart,
    LocalDate periodEnd,
    String label,
    double rawCapacityPd,
    double effectiveCapacityPd,
    double committedLoadPd,
    double capacityGapPd,
    boolean overAllocated
) {}
