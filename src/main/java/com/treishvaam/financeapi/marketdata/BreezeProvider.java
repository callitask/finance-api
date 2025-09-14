package com.treishvaam.financeapi.marketdata;

import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

@Component("apiBreezeProvider")
public class BreezeProvider implements MarketDataProvider {

   @Override
   public List<MarketData> fetchTopGainers() {
       System.out.println("BreezeProvider: fetchTopGainers() - NOT IMPLEMENTED");
       return Collections.emptyList();
   }

   @Override
   public List<MarketData> fetchTopLosers() {
       System.out.println("BreezeProvider: fetchTopLosers() - NOT IMPLEMENTED");
       return Collections.emptyList();
   }

   @Override
   public List<MarketData> fetchMostActive() {
       System.out.println("BreezeProvider: fetchMostActive() - NOT IMPLEMENTED");
       return Collections.emptyList();
   }

   @Override
   public Object fetchHistoricalData(String ticker) {
       System.out.println("BreezeProvider: fetchHistoricalData() - NOT IMPLEMENTED");
       return null; // Or return an empty map/list as appropriate
   }
}