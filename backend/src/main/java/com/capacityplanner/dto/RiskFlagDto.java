package com.capacityplanner.dto;

public record RiskFlagDto(
    FlagType type,
    Severity severity,
    String message,
    String entityType,
    String entityId
) {
    public enum FlagType {
        OVER_ALLOCATION,
        SKILL_SHORTFALL,
        DECOMPOSITION_GAP,
        CRITICAL_RESOURCE
    }

    public enum Severity {
        RED,
        AMBER
    }
}
