package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.config.tenant.TenantContext;
import com.treishvaam.financeapi.marketdata.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Bootstraps essential financial data on application startup.
 *
 * <p>Scope: - Seeds market movers and background historical data.
 *
 * <p>Security Constraints: - MUST strictly execute under the 'finance' tenant context.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Phase 3 (Backend Dynamic Integration):
 * Wrapped execution in explicit TenantContext to guarantee data isolation and prevent seeding
 * during cross-tenant operations.
 *
 * <p>- EDITED (Startup Performance Fix): • Moved the synchronous FMP `fetchAndStoreMarketData` call
 * into a dedicated background daemon thread. • Why: External HTTP calls during `CommandLineRunner`
 * execution block the main thread, preventing Tomcat from fully initializing. This caused Docker
 * health checks to fail (timeout) and the container to crash loop.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
@Component
@Profile("!test")
public class MarketDataInitializer implements CommandLineRunner {

    @Autowired
    @Qualifier("apiMarketDataService")
    private MarketDataService marketDataService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println(
                "Application started. Offloading initial data fetches to background thread...");

        Thread initThread =
                new Thread(
                        () -> {
                            // 1. STRICT TENANT ISOLATION: Initialize exclusively for Finance domain
                            TenantContext.setTenantId("finance");
                            try {
                                try {
                                    // Fetch Market Movers (FMP) - Moved to async to prevent main
                                    // thread blocking
                                    System.out.println("Starting initial market movers fetch...");
                                    marketDataService.fetchAndStoreMarketData("US", "STARTUP");
                                    System.out.println("Initial market movers fetch complete.");
                                } catch (Exception e) {
                                    System.err.println(
                                            "Initial market movers fetch failed: "
                                                    + e.getMessage());
                                }

                                // 2. Run Python script for History + Quotes
                                try {
                                    System.out.println(
                                            "Starting Python script for historical and quote data (async)...");
                                    marketDataService.runPythonHistoryAndQuoteUpdate("STARTUP");
                                    System.out.println(
                                            "Python script (async) startup run complete.");
                                } catch (Exception e) {
                                    System.err.println(
                                            "Python script (async) startup run failed: "
                                                    + e.getMessage());
                                }
                            } finally {
                                TenantContext.clear(); // Always clear context after execution
                            }
                        });

        initThread.setName("MarketData-Init-Thread");
        initThread.setDaemon(true); // Allow JVM to exit gracefully if thread is still running
        initThread.start();
    }
}
