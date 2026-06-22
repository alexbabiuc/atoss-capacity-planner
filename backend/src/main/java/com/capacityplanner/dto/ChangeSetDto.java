package com.capacityplanner.dto;

import com.capacityplanner.entity.ChangeSet;
import java.util.UUID;

public record ChangeSetDto(
    UUID id,
    String timestamp,
    String actor,
    ChangeSet.Source source,
    String reason,
    int changeCount
) {}
