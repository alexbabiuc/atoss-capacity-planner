package com.capacityplanner.controller;

import com.capacityplanner.dto.InitiativeRequest;
import com.capacityplanner.dto.InitiativeSummaryDto;
import com.capacityplanner.entity.Initiative;
import com.capacityplanner.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/initiatives")
@RequiredArgsConstructor
public class InitiativeController {

    private final PlanningService planningService;

    @GetMapping
    public ResponseEntity<List<InitiativeSummaryDto>> listInitiatives() {
        return ResponseEntity.ok(planningService.getInitiativeSummaries());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InitiativeSummaryDto> getInitiative(@PathVariable UUID id) {
        return ResponseEntity.ok(planningService.getInitiativeSummary(id));
    }

    @PostMapping
    public ResponseEntity<Initiative> createInitiative(@RequestBody InitiativeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.createInitiative(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Initiative> updateInitiative(
        @PathVariable UUID id,
        @RequestBody InitiativeRequest req) {
        return ResponseEntity.ok(planningService.updateInitiative(id, req));
    }
}
