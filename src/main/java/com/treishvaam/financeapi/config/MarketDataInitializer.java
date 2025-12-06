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
  private MarketDataService marketDataService;

  @Override
  public void run(String... args) throws Exception {
    System.out.println("Application started. Performing initial data fetches...");

    try {
      // 1. Fetch Market Movers (FMP) - This is fast
      marketDataService.fetchAndStoreMarketData("US", "STARTUP");
      System.out.println("Initial market movers fetch complete.");
    } catch (Exception e) {
      System.err.println("Initial market movers fetch failed: " + e.getMessage());
    }

    // 2. Run Python script for History + Quotes (async)
    // We run this in a new thread so it doesn't block server startup.
    new Thread(
            () -> {
              try {
                System.out.println(
                    "Starting Python script for historical and quote data (async)...");
                marketDataService.runPythonHistoryAndQuoteUpdate("STARTUP");
                System.out.println("Python script (async) startup run complete.");
              } catch (Exception e) {
                System.err.println("Python script (async) startup run failed: " + e.getMessage());
              }
            })
        .start();
  }
}
