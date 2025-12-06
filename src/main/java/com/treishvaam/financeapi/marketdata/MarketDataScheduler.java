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

    // --- FETCH FMP MOVERS (Top Gainers/Losers) ---
    // Runs Monday-Friday at 10 PM UTC (After US Market Close)
    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "UTC")
    public void fetchUsMarketMovers() {
        System.out.println("[Scheduler] Starting: Fetch US Market Movers (FMP)...");
        try {
            marketDataService.fetchAndStoreMarketData("US", "SCHEDULED");
            System.out.println("[Scheduler] Success: US Market Movers fetched.");
        } catch (Exception e) {
            System.err.println("[Scheduler] Failed: US Market Movers - " + e.getMessage());
        }
    }

    // --- RUN PYTHON DATA ENGINE (Global Indices & History) ---
    // Runs every 4 hours (00:00, 04:00, 08:00, etc.)
    // This frequency ensures we capture market closes in Asia, Europe, and US
    // within a reasonable time frame, without overloading the API limits.
    @Scheduled(cron = "0 0 */4 * * *", zone = "UTC")
    public void updateGlobalMarketData() {
        System.out.println("[Scheduler] Starting: Python Market Data Engine (Global Sync)...");
        try {
            // The Python script has "Smart-Sync" logic (Incremental Fetch),
            // so running it frequently is safe and efficient.
            marketDataService.runPythonHistoryAndQuoteUpdate("SCHEDULED");
            System.out.println("[Scheduler] Triggered: Python script execution started.");
        } catch (Exception e) {
            System.err.println("[Scheduler] Failed: Python script trigger - " + e.getMessage());
        }
    }
}