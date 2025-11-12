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

    // --- KEPT: This fetches Gainers/Losers from FMP ---
    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "UTC") // 10 PM UTC
    public void fetchUsMarketMovers() {
        System.out.println("Executing scheduled job: Fetching US Market Movers (FMP)...");
        marketDataService.fetchAndStoreMarketData("US", "SCHEDULED"); // --- MODIFIED ---
        System.out.println("Scheduled job finished.");
    }

    // --- KEPT: But this does nothing as BreezeProvider is empty ---
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Kolkata")
    public void fetchIndianMarketMovers() {
        System.out.println("Executing scheduled job: Fetching Indian Market Movers (Breeze)...");
        marketDataService.fetchAndStoreMarketData("IN", "SCHEDULED"); // --- MODIFIED ---
        System.out.println("Scheduled job finished.");
    }

    // --- NEW: This runs our Python script daily ---
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC") // 1:00 AM UTC
    public void updatePythonData() {
        System.out.println("Executing scheduled job: Running Python script for History and Quotes...");
        try {
            marketDataService.runPythonHistoryAndQuoteUpdate("SCHEDULED");
            System.out.println("Python script scheduled run complete.");
        } catch (Exception e) {
            System.err.println("Python script scheduled run failed: " + e.getMessage());
        }
    }
}