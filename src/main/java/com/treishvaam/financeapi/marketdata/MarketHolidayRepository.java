package com.treishvaam.financeapi.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, LocalDate> {
    boolean existsByHolidayDateAndMarket(LocalDate date, String market);
}
