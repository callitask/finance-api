package com.treishvaam.financeapi.marketdata;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_data")
public class MarketData {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String ticker;
  private String name; // --- NEW: Added company name ---
  private BigDecimal price;
  private BigDecimal changeAmount;
  private String changePercentage;
  private Long volume; // --- NEW: Added trading volume ---
  private String type; // "GAINER", "LOSER", or "ACTIVE"
  private LocalDateTime lastUpdated;

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTicker() {
    return ticker;
  }

  public void setTicker(String ticker) {
    this.ticker = ticker;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public BigDecimal getChangeAmount() {
    return changeAmount;
  }

  public void setChangeAmount(BigDecimal changeAmount) {
    this.changeAmount = changeAmount;
  }

  public String getChangePercentage() {
    return changePercentage;
  }

  public void setChangePercentage(String changePercentage) {
    this.changePercentage = changePercentage;
  }

  public Long getVolume() {
    return volume;
  }

  public void setVolume(Long volume) {
    this.volume = volume;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public LocalDateTime getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(LocalDateTime lastUpdated) {
    this.lastUpdated = lastUpdated;
  }
}
