package com.capacityplanner.controller;

import com.capacityplanner.entity.AvailabilityOverride;
import com.capacityplanner.entity.Person;
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
    public ResponseEntity<List<Person>> listPersons(
        @RequestParam(required = false) UUID teamId) {
        return ResponseEntity.ok(planningService.getPersons(teamId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Person> getPerson(@PathVariable UUID id) {
        return ResponseEntity.ok(planningService.getPerson(id));
    }

    @PostMapping
    public ResponseEntity<Person> createPerson(@RequestBody Person person) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(planningService.createPerson(person));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Person> updatePerson(@PathVariable UUID id, @RequestBody Person person) {
        return ResponseEntity.ok(planningService.updatePerson(id, person));
    }

    /**
     * Add / replace an availability override.
     * This is the on-demand mechanism for modelling attrition, leave, ramp-up.
     * It is also the input to what-if scenarios when called in a scenario context.
     */
    @PostMapping("/{id}/availability-overrides")
    public ResponseEntity<Person> addAvailabilityOverride(
        @PathVariable UUID id,
        @RequestBody AvailabilityOverride override) {
        return ResponseEntity.ok(planningService.addAvailabilityOverride(id, override));
    }

    @DeleteMapping("/{id}/availability-overrides/{index}")
    public ResponseEntity<Person> removeAvailabilityOverride(
        @PathVariable UUID id,
        @PathVariable int index) {
        return ResponseEntity.ok(planningService.removeAvailabilityOverride(id, index));
    }
}
