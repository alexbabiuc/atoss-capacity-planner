package com.capacityplanner.controller;

import com.capacityplanner.dto.PersonDto;
import com.capacityplanner.dto.PersonRequest;
import com.capacityplanner.entity.AvailabilityOverride;
import com.capacityplanner.service.PlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/people")
@RequiredArgsConstructor
public class PersonController {

    private final PlanningService planningService;

    @GetMapping
    public ResponseEntity<List<PersonDto>> listPersons(
        @RequestParam(required = false) UUID teamId) {
        return ResponseEntity.ok(planningService.getPersons(teamId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonDto> getPerson(@PathVariable UUID id) {
        return ResponseEntity.ok(planningService.getPerson(id));
    }

    @PostMapping
    public ResponseEntity<PersonDto> createPerson(@RequestBody PersonRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.createPerson(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonDto> updatePerson(@PathVariable UUID id, @RequestBody PersonRequest req) {
        return ResponseEntity.ok(planningService.updatePerson(id, req));
    }

    @PostMapping("/{id}/availability-overrides")
    public ResponseEntity<PersonDto> addAvailabilityOverride(
        @PathVariable UUID id,
        @RequestBody AvailabilityOverride override) {
        return ResponseEntity.ok(planningService.addAvailabilityOverride(id, override));
    }

    @DeleteMapping("/{id}/availability-overrides/{index}")
    public ResponseEntity<PersonDto> removeAvailabilityOverride(
        @PathVariable UUID id,
        @PathVariable int index) {
        return ResponseEntity.ok(planningService.removeAvailabilityOverride(id, index));
    }
}
