package com.treishvaam.financeapi.marketdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("apiMarketDataScheduler")
public class MarketDataScheduler {
   
   @Autowired
   @Qualifier("apiMarketDataService")
   private MarketDataService marketDataService;

   @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Kolkata")
   public void fetchIndianMarketMovers() {
       System.out.println("Executing scheduled job: Fetching Indian Market Movers...");
       marketDataService.fetchAndStoreMarketData("IN", "AUTOMATIC"); // --- MODIFIED ---
       System.out.println("Scheduled job finished.");
   }

   @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "UTC")
   public void fetchUsMarketMovers() {
       System.out.println("Executing scheduled job: Fetching US Market Movers...");
       marketDataService.fetchAndStoreMarketData("US", "AUTOMATIC"); // --- MODIFIED ---
       System.out.println("Scheduled job finished.");
   }
}