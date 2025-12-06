package com.treishvaam.financeapi.marketdata;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {
  List<MarketData> findByType(String type);

  @Transactional
  void deleteByType(String type);
}
