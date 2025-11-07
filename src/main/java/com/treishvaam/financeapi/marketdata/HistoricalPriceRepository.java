package com.treishvaam.financeapi.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface HistoricalPriceRepository extends JpaRepository<HistoricalPrice, Long> {
    List<HistoricalPrice> findByTickerOrderByPriceDateAsc(String ticker);
    boolean existsByTickerAndPriceDate(String ticker, LocalDate priceDate);
}
