package com.treishvaam.financeapi.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AudienceVisit Entity.
 */
public interface AudienceVisitRepository extends JpaRepository<AudienceVisit, Long> {

    /**
     * Base query fragment for filtering. Reused by data and filter option queries.
     */
    String FILTER_QUERY_BASE = """
        FROM AudienceVisit av 
        WHERE av.sessionDate BETWEEN :startDate AND :endDate
        AND (:country IS NULL OR av.country = :country)
        AND (:region IS NULL OR av.region = :region)
        AND (:city IS NULL OR av.city = :city)
        AND (:operatingSystem IS NULL OR av.operatingSystem = :operatingSystem)
        AND (:osVersion IS NULL OR av.osVersion = :osVersion)
        AND (:sessionSource IS NULL OR av.sessionSource = :sessionSource)
    """; // 'deviceCategory' filter removed

    /**
     * Finds all audience visits within a given date range, with optional dynamic filters.
     */
    @Query("SELECT av " + FILTER_QUERY_BASE + " ORDER BY av.sessionDate DESC")
    List<AudienceVisit> findHistoricalDataWithFilters(
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate, 
            @Param("country") String country, 
            @Param("region") String region,
            @Param("city") String city, // Added back
            // 'deviceCategory' removed
            @Param("operatingSystem") String operatingSystem,
            @Param("osVersion") String osVersion,
            @Param("sessionSource") String sessionSource
    );
            
    /**
     * Gets the latest session date available in the database.
     */
    @Query("SELECT MAX(av.sessionDate) FROM AudienceVisit av")
    Optional<LocalDate> findMaxSessionDate();

    // --- Methods for Dynamic Filter Options ---

    @Query("SELECT DISTINCT av.country " + FILTER_QUERY_BASE + " ORDER BY av.country")
    List<String> findDistinctCountries(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, 
            @Param("country") String country, @Param("region") String region, @Param("city") String city,
            @Param("operatingSystem") String operatingSystem,
            @Param("osVersion") String osVersion, @Param("sessionSource") String sessionSource);

    @Query("SELECT DISTINCT av.region " + FILTER_QUERY_BASE + " ORDER BY av.region")
    List<String> findDistinctRegions(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, 
            @Param("country") String country, @Param("region") String region, @Param("city") String city,
            @Param("operatingSystem") String operatingSystem,
            @Param("osVersion") String osVersion, @Param("sessionSource") String sessionSource);

    @Query("SELECT DISTINCT av.city " + FILTER_QUERY_BASE + " ORDER BY av.city") // Added back
    List<String> findDistinctCities(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, 
            @Param("country") String country, @Param("region") String region, @Param("city") String city,
            @Param("operatingSystem") String operatingSystem,
            @Param("osVersion") String osVersion, @Param("sessionSource") String sessionSource);

    // 'findDistinctDeviceCategories' method removed

    @Query("SELECT DISTINCT av.operatingSystem " + FILTER_QUERY_BASE + " ORDER BY av.operatingSystem")
    List<String> findDistinctOperatingSystems(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, 
            @Param("country") String country, @Param("region") String region, @Param("city") String city,
            @Param("operatingSystem") String operatingSystem,
            @Param("osVersion") String osVersion, @Param("sessionSource") String sessionSource);

    @Query("SELECT DISTINCT av.osVersion " + FILTER_QUERY_BASE + " ORDER BY av.osVersion")
    List<String> findDistinctOsVersions(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, 
            @Param("country") String country, @Param("region") String region, @Param("city") String city,
            @Param("operatingSystem") String operatingSystem,
            @Param("osVersion") String osVersion, @Param("sessionSource") String sessionSource);
            
    @Query("SELECT DISTINCT av.sessionSource " + FILTER_QUERY_BASE + " ORDER BY av.sessionSource")
    List<String> findDistinctSessionSources(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, 
            @Param("country") String country, @Param("region") String region, @Param("city") String city,
            @Param("operatingSystem") String operatingSystem,
            @Param("osVersion") String osVersion, @Param("sessionSource") String sessionSource);
}