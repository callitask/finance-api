package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.AnalyticsEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * AI-CONTEXT: IMMUTABLE CHANGE HISTORY: - ADDED (Phase 5): Repository for AnalyticsEvent with purge
 * capability.
 */
@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Modifying
    @Query("DELETE FROM AnalyticsEvent a WHERE a.createdAt < :cutoffDate")
    void deleteEventsOlderThan(@Param("cutoffDate") Instant cutoffDate);
}
