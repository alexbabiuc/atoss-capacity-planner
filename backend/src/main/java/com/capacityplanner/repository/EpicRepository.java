package com.capacityplanner.repository;

import com.capacityplanner.entity.Epic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EpicRepository extends JpaRepository<Epic, UUID> {

    List<Epic> findByTeamId(UUID teamId);

    List<Epic> findByInitiativeId(UUID initiativeId);

    @Query("""
        SELECT e FROM Epic e
        WHERE e.team.id = :teamId
          AND e.status NOT IN ('DONE', 'DEFERRED')
          AND e.dueDate >= :periodStart
          AND e.startDate <= :periodEnd
        """)
    List<Epic> findActiveEpicsForTeamInPeriod(
        @Param("teamId") UUID teamId,
        @Param("periodStart") LocalDate periodStart,
        @Param("periodEnd") LocalDate periodEnd);
}
