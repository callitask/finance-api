package com.treishvaam.financeapi.marketdata;

import java.util.List; // --- NEW IMPORT ---
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuoteDataRepository extends JpaRepository<QuoteData, String> {
  // --- NEW METHOD ---
  List<QuoteData> findByTickerIn(List<String> tickers);
}
