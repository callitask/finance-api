package com.treishvaam.financeapi.marketdata;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Schedules automated fetching and updating of market data.
 *
 * <p>Scope: - Triggers internal services on a cron schedule.
 *
 * <p>Critical Dependencies: - Backend: MarketDataService.
 *
 * <p>Security Constraints: - Must handle exceptions gracefully to avoid killing the scheduler
 * thread.
 *
 * <p>Non-Negotiables: - Ensure cron zones are explicitly UTC to avoid server timezone drift.
 *
 * <p>Change Intent: - Implement ARCH-03: Transition heavy Python subprocess spawning to a RabbitMQ
 * event-driven architecture.
 *
 * <p>Future AI Guidance: - Do not revert to synchronous/blocking script execution.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Phase 5 - ARCH-03): • Replaced
 * `marketDataService.runPythonHistoryAndQuoteUpdate("SCHEDULED");` with
 * `marketDataService.enqueueMarketUpdate("SCHEDULED");`. • Why: Offload the blocking ProcessBuilder
 * execution to an asynchronous RabbitMQ queue consumer, preventing Thread pool exhaustion during
 * load.
 */
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
      // ARCH-03: Enqueue via RabbitMQ instead of direct execution
      marketDataService.enqueueMarketUpdate("SCHEDULED");
      System.out.println("[Scheduler] Triggered: Python script execution event published.");
    } catch (Exception e) {
      System.err.println("[Scheduler] Failed: Python script trigger - " + e.getMessage());
    }
  }
}
