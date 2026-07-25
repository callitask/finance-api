package com.treishvaam.financeapi.marketdata;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Repository interface for managing MarketData entities.
 *
 * <p>Scope: - Provides database access operations for market movers (gainers, losers, active).
 *
 * <p>Critical Dependencies: - Backend: MarketDataService relies on this for scheduled and manual
 * refreshes.
 *
 * <p>Security Constraints: - Must safely execute bulk deletions without causing database locks or
 * race conditions.
 *
 * <p>Non-Negotiables: - Do not revert native `@Modifying` queries back to JPA method name
 * derivations for bulk deletes.
 *
 * <p>Change Intent: - Upgraded `deleteByType` to a native JPQL `@Modifying` query to eliminate
 * `ObjectOptimisticLockingFailureException` race conditions caused by JPA's select-then-delete
 * behavior during concurrent refresh operations.
 *
 * <p>Future AI Guidance: - Maintain `@Modifying` and `@Query` annotations for bulk deletions.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Vol 2 - Database Lock Fix): • Converted
 * `deleteByType` from an implicit JPA derived query to an explicit `@Modifying` `@Query` to perform
 * single-pass atomic deletions, preventing optimistic locking exceptions when scheduled and manual
 * jobs overlap.
 *
 * <p>- DO-NOT-DELETE RULE (ABSOLUTE): This IMMUTABLE CHANGE HISTORY section acts as the
 * institutional memory for future AI sessions. It must never be deleted, truncated, rewritten, or
 * regenerated. Future AI must append only.
 */
@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    List<MarketData> findByType(String type);

    @Modifying
    @Transactional
    @Query("DELETE FROM MarketData m WHERE m.type = :type")
    void deleteByType(@Param("type") String type);
}
