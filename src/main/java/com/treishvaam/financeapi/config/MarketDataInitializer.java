package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.marketdata.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class MarketDataInitializer implements CommandLineRunner {

    @Autowired
    @Qualifier("apiMarketDataService")
    // This variable name is now corrected
    private MarketDataService marketDataService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Application started. Performing initial fetch of market data...");
        try {
            // This now correctly calls the service
            marketDataService.fetchAndStoreMarketData("US", "STARTUP");
            System.out.println("Initial market data fetch complete.");
        } catch (Exception e) {
            System.err.println("Initial market data fetch failed: " + e.getMessage());
        }
    }
}