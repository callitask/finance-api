package com.treishvaam.finance.marketdata.repository;

import com.treishvaam.finance.marketdata.entity.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {
    List<MarketData> findByType(String type);

    @Transactional
    void deleteByType(String type);
}