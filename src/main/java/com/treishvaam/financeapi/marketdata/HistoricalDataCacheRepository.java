package com.treishvaam.financeapi.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HistoricalDataCacheRepository extends JpaRepository<HistoricalDataCache, String> {

    Optional<HistoricalDataCache> findByTicker(String ticker);
}
