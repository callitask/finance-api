package com.treishvaam.financeapi.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;

@Entity
public class HistoricalDataCache {

    @Id
    @Column(unique = true, nullable = false)
    private String ticker;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String data;

    private LocalDateTime lastFetched;

    // Constructors
    public HistoricalDataCache() {}

    public HistoricalDataCache(String ticker, String data, LocalDateTime lastFetched) {
        this.ticker = ticker;
        this.data = data;
        this.lastFetched = lastFetched;
    }

    // Getters and Setters
    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public LocalDateTime getLastFetched() {
        return lastFetched;
    }

    public void setLastFetched(LocalDateTime lastFetched) {
        this.lastFetched = lastFetched;
    }
}