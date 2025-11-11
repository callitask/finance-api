package com.treishvaam.financeapi.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AudienceVisit Entity.
 * Moved from com.treishvaam.financeapi.repository to consolidate analytics components.
 */
public interface AudienceVisitRepository extends JpaRepository<AudienceVisit, Long> {

    /**
     * Finds all audience visits within a given date range.
     */
    List<AudienceVisit> findBySessionDateBetweenOrderBySessionDateDesc(LocalDate startDate, LocalDate endDate);

    /**
     * Gets the latest session date available in the database.
     */
    @Query("SELECT MAX(av.sessionDate) FROM AudienceVisit av")
    Optional<LocalDate> findMaxSessionDate();
}