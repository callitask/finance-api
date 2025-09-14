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

   // --- NEW: Inject the AlphaVantage provider specifically for historical data ---
   @Autowired
   @Qualifier("alphaVantageProvider")
   private MarketDataProvider alphaVantageProvider;

   /**
    * Gets the provider for fetching top movers data (Gainers, Losers, Active).
    * @param market The market identifier ("US", "IN", etc.)
    * @return The appropriate MarketDataProvider.
    */
   public MarketDataProvider getMoversProvider(String market) {
       if ("IN".equalsIgnoreCase(market)) {
           return breezeProvider;
       }
       // Default to FMP for US and other global markets for top movers.
       return fmpProvider;
   }

   /**
    * Gets the dedicated provider for fetching historical chart data.
    * @return The MarketDataProvider for historical data.
    */
   public MarketDataProvider getHistoricalDataProvider() {
       // Alpha Vantage is the dedicated provider for historical data.
       return alphaVantageProvider;
   }
}