package com.treishvaam.financeapi.marketdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("apiMarketDataFactory")
public class MarketDataFactory {

  @Autowired
  @Qualifier("apiFmpProvider")
  private MarketDataProvider fmpProvider;

  @Autowired
  @Qualifier("apiBreezeProvider")
  private MarketDataProvider breezeProvider;

  @Autowired
  @Qualifier("alphaVantageProvider")
  private MarketDataProvider alphaVantageProvider;

  // --- NEW: Finnhub Provider ---
  @Autowired
  @Qualifier("finnhubProvider")
  private FinnhubProvider finnhubProvider;

  public MarketDataProvider getMoversProvider(String market) {
    if ("IN".equalsIgnoreCase(market)) {
      return breezeProvider;
    }
    return fmpProvider;
  }

  public MarketDataProvider getHistoricalDataProvider() {
    return alphaVantageProvider;
  }

  // --- NEW Getter ---
  public FinnhubProvider getQuoteProvider() {
    return finnhubProvider;
  }
}
