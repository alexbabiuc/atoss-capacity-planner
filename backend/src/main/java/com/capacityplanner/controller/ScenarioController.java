package com.capacityplanner.controller;

import com.capacityplanner.dto.ChangeSetDto;
import com.capacityplanner.dto.ScenarioImpactDto;
import com.capacityplanner.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final PlanningService planningService;

    /**
     * Preview the impact of a proposed scenario without committing it.
     * Returns a FlagDiff: which risk flags newly light up vs baseline,
     * and which clear. Expensive — UI should debounce.
     */
    @PostMapping("/preview")
    public ResponseEntity<ScenarioImpactDto> previewImpact(
        @RequestBody ScenarioPreviewRequest request) {
        return ResponseEntity.ok(planningService.previewScenario(request));
    }

    /**
     * Commit a scenario to the baseline.
     * Writes a ChangeSet entry with the provided reason. Reason is required.
     */
    @PostMapping("/commit")
    public ResponseEntity<ChangeSetDto> commit(@RequestBody ScenarioCommitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.commitScenario(request));
    }

    // ── Request bodies ────────────────────────────────────────────────────────

    public record ScenarioPreviewRequest(List<DeltaRequest> deltas) {}

    public record ScenarioCommitRequest(
        List<DeltaRequest> deltas,
        String reason,        // required, non-empty
        String source         // CHANGE_REQUEST | TEAM_FEEDBACK
    ) {}

    public record DeltaRequest(
        String entityType,
        String entityId,
        String field,
        Object newValue) {}
}
