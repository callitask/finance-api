package com.treishvaam.financeapi.marketdata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
// --- UPDATED: Gave this component a unique bean name to resolve the conflict ---
@Component("apiMarketDataFactory")
public class MarketDataFactory {
   @Autowired
   @Qualifier("apiFmpProvider")
   private MarketDataProvider fmpProvider;
   @Autowired
   @Qualifier("apiBreezeProvider")
   private MarketDataProvider breezeProvider;
   public MarketDataProvider getProvider(String market) {
       // As per the roadmap, FMP is primary for US, Breeze is primary for India.
       if ("IN".equalsIgnoreCase(market)) {
           return breezeProvider;
       }
       // Default to FMP for US and other global markets for now.
       return fmpProvider;
   }
}
