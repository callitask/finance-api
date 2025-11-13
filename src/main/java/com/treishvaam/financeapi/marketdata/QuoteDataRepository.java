package com.treishvaam.financeapi.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List; // --- NEW IMPORT ---

@Repository
public interface QuoteDataRepository extends JpaRepository<QuoteData, String> {
    // --- NEW METHOD ---
    List<QuoteData> findByTickerIn(List<String> tickers);
}