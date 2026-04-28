/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Data Access Object for Audience Analytics.
 *
 * <p>Scope: - Handles complex dynamic filtering via JPQL.
 *
 * <p>Critical Dependencies: - Backend: AnalyticsService relies on these distinct signatures.
 *
 * <p>Security Constraints: - Parameters must remain natively bound (@Param) to prevent SQL
 * injection. - Boolean flags (:hasExcludes) MUST be used to prevent empty collection crashes in
 * Postgres/MySQL.
 *
 * <p>Non-Negotiables: - Modifying signature lengths requires parallel updates in AnalyticsService.
 *
 * <p>Change Intent: - Add `findFirstVisitDatesByClientIds` to efficiently batch-calculate First
 * Visit Dates (O(1) database queries instead of N+1). - Inject `clientId` and `excludeClientIds`
 * into all filtering logic.
 *
 * <p>Future AI Guidance: - Ensure `hasExcludes = false` is sent when the exclude list is null or
 * empty, alongside a dummy list to satisfy Hibernate parsing.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Implemented
 * `findFirstVisitDatesByClientIds` aggregation. • Added `clientId`, `hasExcludes`, and
 * `excludeClientIds` to all JPQL historical query signatures.
 */
package com.treishvaam.financeapi.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AudienceVisitRepository extends JpaRepository<AudienceVisit, Long> {

  @Query("SELECT MAX(av.sessionDate) FROM AudienceVisit av")
  Optional<LocalDate> findMaxSessionDate();

  @Query(
      "SELECT av FROM AudienceVisit av WHERE av.sessionId = :sessionId AND av.sessionDate = :date")
  List<AudienceVisit> findBySessionIdAndDate(
      @Param("sessionId") String sessionId, @Param("date") LocalDate date);

  // Aggregation for First Visit Date mapping
  @Query(
      "SELECT av.clientId, MIN(av.sessionDate) FROM AudienceVisit av WHERE av.clientId IN :clientIds GROUP BY av.clientId")
  List<Object[]> findFirstVisitDatesByClientIds(@Param("clientIds") List<String> clientIds);

  @Query(
      "SELECT DISTINCT av.country FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source) "
          + "AND (:clientId IS NULL OR av.clientId = :clientId) "
          + "AND (:hasExcludes = false OR av.clientId NOT IN :excludeClientIds)")
  List<String> findDistinctCountries(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source,
      @Param("clientId") String clientId,
      @Param("hasExcludes") boolean hasExcludes,
      @Param("excludeClientIds") List<String> excludeClientIds);

  @Query(
      "SELECT DISTINCT av.region FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source) "
          + "AND (:clientId IS NULL OR av.clientId = :clientId) "
          + "AND (:hasExcludes = false OR av.clientId NOT IN :excludeClientIds)")
  List<String> findDistinctRegions(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source,
      @Param("clientId") String clientId,
      @Param("hasExcludes") boolean hasExcludes,
      @Param("excludeClientIds") List<String> excludeClientIds);

  @Query(
      "SELECT DISTINCT av.city FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source) "
          + "AND (:clientId IS NULL OR av.clientId = :clientId) "
          + "AND (:hasExcludes = false OR av.clientId NOT IN :excludeClientIds)")
  List<String> findDistinctCities(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source,
      @Param("clientId") String clientId,
      @Param("hasExcludes") boolean hasExcludes,
      @Param("excludeClientIds") List<String> excludeClientIds);

  @Query(
      "SELECT DISTINCT av.operatingSystem FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source) "
          + "AND (:clientId IS NULL OR av.clientId = :clientId) "
          + "AND (:hasExcludes = false OR av.clientId NOT IN :excludeClientIds)")
  List<String> findDistinctOperatingSystems(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("osVersion") String osVersion,
      @Param("source") String source,
      @Param("clientId") String clientId,
      @Param("hasExcludes") boolean hasExcludes,
      @Param("excludeClientIds") List<String> excludeClientIds);

  @Query(
      "SELECT DISTINCT av.osVersion FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:source IS NULL OR av.sessionSource = :source) "
          + "AND (:clientId IS NULL OR av.clientId = :clientId) "
          + "AND (:hasExcludes = false OR av.clientId NOT IN :excludeClientIds)")
  List<String> findDistinctOsVersions(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("source") String source,
      @Param("clientId") String clientId,
      @Param("hasExcludes") boolean hasExcludes,
      @Param("excludeClientIds") List<String> excludeClientIds);

  @Query(
      "SELECT DISTINCT av.sessionSource FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:clientId IS NULL OR av.clientId = :clientId) "
          + "AND (:hasExcludes = false OR av.clientId NOT IN :excludeClientIds)")
  List<String> findDistinctSessionSources(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("clientId") String clientId,
      @Param("hasExcludes") boolean hasExcludes,
      @Param("excludeClientIds") List<String> excludeClientIds);

  @Query(
      "SELECT av FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source) "
          + "AND (:clientId IS NULL OR av.clientId = :clientId) "
          + "AND (:hasExcludes = false OR av.clientId NOT IN :excludeClientIds) "
          + "ORDER BY av.sessionDate DESC, av.createdAt DESC")
  List<AudienceVisit> findHistoricalDataWithFilters(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source,
      @Param("clientId") String clientId,
      @Param("hasExcludes") boolean hasExcludes,
      @Param("excludeClientIds") List<String> excludeClientIds);
}
