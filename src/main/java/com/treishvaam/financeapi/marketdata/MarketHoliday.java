package com.treishvaam.financeapi.marketdata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "market_holiday")
public class MarketHoliday {
    @Id
    private LocalDate holidayDate;
    private String eventName;
    private String market; // e.g., "US"

    // Getters and Setters
    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
}
