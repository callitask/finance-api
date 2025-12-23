package com.treishvaam.financeapi.marketdata;

import java.util.List;

public class WidgetDataDto {
  private QuoteData quoteData;
  private List<HistoricalPrice> historicalData;
  private List<QuoteData> peers;

  // --- REQUIRED: No-Argument Constructor for JSON Deserialization ---
  public WidgetDataDto() {}

  public WidgetDataDto(
      QuoteData quoteData, List<HistoricalPrice> historicalData, List<QuoteData> peers) {
    this.quoteData = quoteData;
    this.historicalData = historicalData;
    this.peers = peers;
  }

  // Getters
  public QuoteData getQuoteData() {
    return quoteData;
  }

  public List<HistoricalPrice> getHistoricalData() {
    return historicalData;
  }

  public List<QuoteData> getPeers() {
    return peers;
  }

  // --- REQUIRED: Setters for JSON Deserialization ---
  public void setQuoteData(QuoteData quoteData) {
    this.quoteData = quoteData;
  }

  public void setHistoricalData(List<HistoricalPrice> historicalData) {
    this.historicalData = historicalData;
  }

  public void setPeers(List<QuoteData> peers) {
    this.peers = peers;
  }
}
