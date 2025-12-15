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
  Optional<AudienceVisit> findBySessionIdAndDate(
      @Param("sessionId") String sessionId, @Param("date") LocalDate date);

  @Query(
      "SELECT DISTINCT av.country FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source)")
  List<String> findDistinctCountries(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source);

  @Query(
      "SELECT DISTINCT av.region FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source)")
  List<String> findDistinctRegions(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source);

  @Query(
      "SELECT DISTINCT av.city FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source)")
  List<String> findDistinctCities(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source);

  @Query(
      "SELECT DISTINCT av.operatingSystem FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source)")
  List<String> findDistinctOperatingSystems(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("osVersion") String osVersion,
      @Param("source") String source);

  @Query(
      "SELECT DISTINCT av.osVersion FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:source IS NULL OR av.sessionSource = :source)")
  List<String> findDistinctOsVersions(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("source") String source);

  @Query(
      "SELECT DISTINCT av.sessionSource FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion)")
  List<String> findDistinctSessionSources(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion);

  @Query(
      "SELECT av FROM AudienceVisit av "
          + "WHERE av.sessionDate BETWEEN :startDate AND :endDate "
          + "AND (:country IS NULL OR av.country = :country) "
          + "AND (:region IS NULL OR av.region = :region) "
          + "AND (:city IS NULL OR av.city = :city) "
          + "AND (:os IS NULL OR av.operatingSystem = :os) "
          + "AND (:osVersion IS NULL OR av.osVersion = :osVersion) "
          + "AND (:source IS NULL OR av.sessionSource = :source) "
          + "ORDER BY av.sessionDate DESC")
  List<AudienceVisit> findHistoricalDataWithFilters(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("country") String country,
      @Param("region") String region,
      @Param("city") String city,
      @Param("os") String os,
      @Param("osVersion") String osVersion,
      @Param("source") String source);
}
