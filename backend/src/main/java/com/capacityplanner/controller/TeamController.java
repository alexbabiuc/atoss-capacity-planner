package com.capacityplanner.controller;

import com.capacityplanner.dto.TeamSummaryDto;
import com.capacityplanner.entity.Team;
import com.capacityplanner.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final PlanningService planningService;

    /** List all teams */
    @GetMapping
    public ResponseEntity<List<TeamSummaryDto>> listTeams(
        @RequestParam(defaultValue = "MONTH") String granularity,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(planningService.getTeamSummaries(granularity, from, to));
    }

    /** Single team with full capacity breakdown */
    @GetMapping("/{id}")
    public ResponseEntity<TeamSummaryDto> getTeam(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "MONTH") String granularity,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(planningService.getTeamSummary(id, granularity, from, to));
    }

    @PostMapping
    public ResponseEntity<Team> createTeam(@RequestBody Team team) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(planningService.createTeam(team));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Team> updateTeam(@PathVariable UUID id, @RequestBody Team team) {
        return ResponseEntity.ok(planningService.updateTeam(id, team));
    }
}
