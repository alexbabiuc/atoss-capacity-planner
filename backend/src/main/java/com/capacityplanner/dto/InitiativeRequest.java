package com.capacityplanner.dto;

import java.time.LocalDate;

public record InitiativeRequest(
    String name,
    String description,
    Double topDownEstimate,
    LocalDate startDate,
    LocalDate targetDeliveryDate,
    Integer priority,
    String ownerName,
    String status
) {}
