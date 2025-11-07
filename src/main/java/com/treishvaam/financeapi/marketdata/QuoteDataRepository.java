package com.treishvaam.financeapi.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuoteDataRepository extends JpaRepository<QuoteData, String> {
}
