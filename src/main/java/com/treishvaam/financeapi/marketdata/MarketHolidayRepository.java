package com.treishvaam.financeapi.marketdata;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, LocalDate> {
  boolean existsByHolidayDateAndMarket(LocalDate date, String market);
}
