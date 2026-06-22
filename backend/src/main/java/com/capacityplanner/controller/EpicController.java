package com.capacityplanner.controller;

import com.capacityplanner.dto.EpicSummaryDto;
import com.capacityplanner.entity.Epic;
import com.capacityplanner.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/epics")
@RequiredArgsConstructor
public class EpicController {

    private final PlanningService planningService;

    @GetMapping
    public ResponseEntity<List<EpicSummaryDto>> listEpics(
        @RequestParam(required = false) UUID teamId,
        @RequestParam(required = false) UUID initiativeId
    ) {
        return ResponseEntity.ok(planningService.getEpicSummaries(teamId, initiativeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EpicSummaryDto> getEpic(@PathVariable UUID id) {
        return ResponseEntity.ok(planningService.getEpicSummary(id));
    }

    @PostMapping
    public ResponseEntity<Epic> createEpic(@RequestBody Epic epic) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(planningService.createEpic(epic));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Epic> updateEpic(@PathVariable UUID id, @RequestBody Epic epic) {
        return ResponseEntity.ok(planningService.updateEpic(id, epic));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEpic(@PathVariable UUID id) {
        planningService.deleteEpic(id);
        return ResponseEntity.noContent().build();
    }
}
