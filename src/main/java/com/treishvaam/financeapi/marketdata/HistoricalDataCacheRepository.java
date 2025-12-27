package com.treishvaam.financeapi.marketdata;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistoricalDataCacheRepository extends JpaRepository<HistoricalDataCache, String> {

  Optional<HistoricalDataCache> findByTicker(String ticker);
}
