package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.marketdata.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test") // This ensures the initializer does not run during automated tests
public class MarketDataInitializer implements CommandLineRunner {

    @Autowired
    // We use @Qualifier to specify exactly which MarketDataService bean to use
    @Qualifier("apiMarketDataService")
    private MarketDataService marketDataService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Application started. Performing initial fetch of market data...");
        try {
            marketDataService.fetchAndStoreMarketData("US");
            System.out.println("Initial market data fetch complete.");
        } catch (Exception e) {
            System.err.println("Initial market data fetch failed: " + e.getMessage());
        }
    }
}
