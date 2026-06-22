package com.capacityplanner.controller;

import com.capacityplanner.dto.ChangeSetDto;
import com.capacityplanner.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/changelog")
@RequiredArgsConstructor
public class ChangeLogController {

    private final PlanningService planningService;

    /** Full log, newest first */
    @GetMapping
    public ResponseEntity<List<ChangeSetDto>> getLog() {
        return ResponseEntity.ok(planningService.getChangeLog());
    }

    /**
     * History for a specific entity — e.g. /changelog?entityType=Epic&entityId=<uuid>
     * Lets a user see every committed change that touched a given epic or initiative.
     */
    @GetMapping("/entity")
    public ResponseEntity<List<ChangeSetDto>> getEntityLog(
        @RequestParam String entityType,
        @RequestParam String entityId
    ) {
        return ResponseEntity.ok(planningService.getChangeLogForEntity(entityType, entityId));
    }
}
