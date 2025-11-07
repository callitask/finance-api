package com.treishvaam.financeapi.marketdata;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "historical_price", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ticker", "priceDate"})
})
public class HistoricalPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ticker;
    private LocalDate priceDate;
    private BigDecimal closePrice;

    public HistoricalPrice() {}

    public HistoricalPrice(String ticker, LocalDate priceDate, BigDecimal closePrice) {
        this.ticker = ticker;
        this.priceDate = priceDate;
        this.closePrice = closePrice;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public LocalDate getPriceDate() { return priceDate; }
    public void setPriceDate(LocalDate priceDate) { this.priceDate = priceDate; }
    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }
}
