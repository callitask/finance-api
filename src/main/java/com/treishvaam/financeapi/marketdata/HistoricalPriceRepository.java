package com.treishvaam.financeapi.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HistoricalPriceRepository extends JpaRepository<HistoricalPrice, Long> {
    List<HistoricalPrice> findByTickerOrderByPriceDateAsc(String ticker);
    boolean existsByTickerAndPriceDate(String ticker, LocalDate priceDate);
    boolean existsByTicker(String ticker);
    // NEW: Find the very last record we have for a ticker
    Optional<HistoricalPrice> findTopByTickerOrderByPriceDateDesc(String ticker);
}