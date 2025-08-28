package com.treishvaam.financeapi.marketdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataScheduler {

    @Autowired
    private MarketDataService marketDataService;

    /**
     * HIGH-PRIORITY DAILY TASK (as per Roadmap Section 2.3)
     * Fetches Top Gainers/Losers for the Indian market after closing time.
     * This is the most critical daily scheduled job.
     */
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Kolkata")
    public void fetchIndianMarketMovers() {
        System.out.println("Executing scheduled job: Fetching Indian Market Movers...");
        marketDataService.fetchAndStoreMarketData("IN");
        System.out.println("Scheduled job finished.");
    }

    /**
     * DAILY TASK for US Market
     * Fetches data for the US market. Can be run at a different time.
     */
    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "UTC") // Example: 10 PM UTC
    public void fetchUsMarketMovers() {
        System.out.println("Executing scheduled job: Fetching US Market Movers...");
        marketDataService.fetchAndStoreMarketData("US");
        System.out.println("Scheduled job finished.");
    }
}