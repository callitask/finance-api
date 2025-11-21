package com.treishvaam.financeapi.marketdata;

import java.util.List;

public class WidgetDataDto {
    private QuoteData quoteData;
    private List<HistoricalPrice> historicalData;
    private List<QuoteData> peers; 

    public WidgetDataDto(QuoteData quoteData, List<HistoricalPrice> historicalData, List<QuoteData> peers) {
        this.quoteData = quoteData;
        this.historicalData = historicalData;
        this.peers = peers; 
    }

    // Getters
    public QuoteData getQuoteData() { return quoteData; }
    public List<HistoricalPrice> getHistoricalData() { return historicalData; }
    public List<QuoteData> getPeers() { return peers; } 
}