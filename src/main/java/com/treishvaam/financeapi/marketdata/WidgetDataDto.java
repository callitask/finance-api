package com.treishvaam.financeapi.marketdata;

import java.util.List;

public class WidgetDataDto {
    private QuoteData quoteData;
    private List<HistoricalPrice> historicalData;

    public WidgetDataDto(QuoteData quoteData, List<HistoricalPrice> historicalData) {
        this.quoteData = quoteData;
        this.historicalData = historicalData;
    }

    // Getters
    public QuoteData getQuoteData() { return quoteData; }
    public List<HistoricalPrice> getHistoricalData() { return historicalData; }
}
