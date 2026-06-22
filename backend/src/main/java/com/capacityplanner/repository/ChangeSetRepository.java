package com.capacityplanner.repository;

import com.capacityplanner.entity.ChangeSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChangeSetRepository extends JpaRepository<ChangeSet, UUID> {

    List<ChangeSet> findByOrderByTimestampDesc();

    @Query("""
        SELECT cs FROM ChangeSet cs
        JOIN cs.changes d
        WHERE d.entityType = :entityType AND d.entityId = :entityId
        ORDER BY cs.timestamp DESC
        """)
    List<ChangeSet> findByAffectedEntity(
        @Param("entityType") String entityType,
        @Param("entityId") String entityId);
}
