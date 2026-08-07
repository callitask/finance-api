/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Data Access Object for Audience Analytics.
 *
 * <p>Scope: - Handles complex dynamic filtering via JPQL and dynamic Specifications.
 *
 * <p>Security Constraints: - Parameters must remain natively bound (@Param) to prevent SQL
 * injection. - The delete GA4 query MUST strictly target sessionId = 'Not available (GA4)' to
 * prevent wiping real Faro RUM data.
 *
 * <p>Change Intent: - Added `clearAutomatically = true, flushAutomatically = true` to
 * `deleteGA4DataForDateRange` to solve the GA4 sync race condition.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Upgraded single `clientId` to List
 * `targetClientIds`. • Added `deleteGA4DataForDateRange` with explicit Hibernate flushing. - EDITED
 * (LATEST): • Added `findFaroVisitsForEnrichment` to support the Smart Attribution Enrichment pool.
 * - EDITED (Incident 31 - DB Bottleneck Eradication): • Added `findAllFirstVisitDatesUpTo`
 * utilizing a parameter-free grouped index scan. • Why: The previous
 * `findFirstVisitDatesByClientIds` used an expanding `IN (...)` clause which caused MariaDB parser
 * lockups and Service Worker timeouts on large datasets. Replaced with native database-level `GROUP
 * BY`. - EDITED (Incident 76 - MariaDB Typed-NULL PreparedStatement Fix): • Refactored to extend
 * JpaSpecificationExecutor. • Deleted all monolithic JPQL filter queries (findDistinctX,
 * findHistoricalDataWithFilters) to eradicate the MariaDB `(? IS NULL)` PreparedStatement
 * evaluation collapse.
 */
package com.treishvaam.financeapi.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AudienceVisitRepository
        extends JpaRepository<AudienceVisit, Long>, JpaSpecificationExecutor<AudienceVisit> {

    @Query("SELECT MAX(av.sessionDate) FROM AudienceVisit av")
    Optional<LocalDate> findMaxSessionDate();

    @Query(
            "SELECT av FROM AudienceVisit av WHERE av.sessionId = :sessionId AND av.sessionDate = :date")
    List<AudienceVisit> findBySessionIdAndDate(
            @Param("sessionId") String sessionId, @Param("date") LocalDate date);

    // DEPRECATED: Causes IN (...) parameter explosion and Service Worker timeouts on large datasets
    @Query(
            "SELECT av.clientId, MIN(av.sessionDate) FROM AudienceVisit av WHERE av.clientId IN :clientIds GROUP BY av.clientId")
    List<Object[]> findFirstVisitDatesByClientIds(@Param("clientIds") List<String> clientIds);

    // ENTERPRISE FIX: Optimized First-Visit Calculation (Zero JVM IN parameter overhead)
    @Query(
            "SELECT av.clientId, MIN(av.sessionDate) FROM AudienceVisit av "
                    + "WHERE av.sessionDate <= :endDate AND av.clientId IS NOT NULL AND av.clientId != 'Not available (GA4)' "
                    + "GROUP BY av.clientId")
    List<Object[]> findAllFirstVisitDatesUpTo(@Param("endDate") LocalDate endDate);

    // Protected GA4 Wipe - Flushing enforces synchronous database execution
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "DELETE FROM AudienceVisit av WHERE av.sessionDate >= :startDate AND av.sessionDate <= :endDate AND av.sessionId = 'Not available (GA4)'")
    void deleteGA4DataForDateRange(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Fetches strictly Faro RUM data (ignoring GA4 placeholders) for the daily Enrichment Pool
    @Query(
            "SELECT av FROM AudienceVisit av WHERE av.sessionDate = :date AND av.sessionId != 'Not available (GA4)'")
    List<AudienceVisit> findFaroVisitsForEnrichment(@Param("date") LocalDate date);
}
