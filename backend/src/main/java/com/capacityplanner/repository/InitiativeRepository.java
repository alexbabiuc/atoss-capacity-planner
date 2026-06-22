package com.capacityplanner.repository;

import com.capacityplanner.entity.Initiative;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InitiativeRepository extends JpaRepository<Initiative, UUID> {
    List<Initiative> findByOrderByPriorityAsc();
}
