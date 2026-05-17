package com.treishvaam.financeapi.marketdata;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - REST Controller for exposing market data, top movers, historical data, and
 * individual quotes.
 *
 * <p>Scope: - Handles all `/api/v1/market` public and admin routes.
 *
 * <p>Critical Dependencies: - MarketDataService for fetching aggregated widget data. -
 * QuoteDataRepository for direct live quote lookups.
 *
 * <p>Security Constraints: - Admin routes (`/admin/**`) must remain protected by
 * `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`. - Public routes (`/data`, `/quote`, `/widget`) do
 * not require authentication.
 *
 * <p>Non-Negotiables: - Endpoint paths must match exactly what the Next.js frontend expects to
 * avoid 404s/500s.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Implemented
 * `@GetMapping("/quote/{ticker}")` and `@GetMapping("/data/{ticker}")`. • Injected
 * `QuoteDataRepository`. • Why: Resolves BUG-01 where frontend requested these endpoints but they
 * did not exist, causing 500 errors via Nginx. • Date: 2026-05-17 (Phase 1 Bug Fixes)
 */
import com.treishvaam.financeapi.apistatus.PasswordDto;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

  @Autowired
  @Qualifier("apiMarketDataService")
  private MarketDataService marketDataService;

  @Autowired private QuoteDataRepository quoteDataRepository;

  @PostMapping("/quotes/batch")
  public ResponseEntity<List<QuoteData>> getBatchQuotes(@RequestBody List<String> tickers) {
    return ResponseEntity.ok(marketDataService.getQuotesBatch(tickers));
  }

  @GetMapping("/widget")
  public ResponseEntity<WidgetDataDto> getWidgetData(@RequestParam String ticker) {
    return ResponseEntity.ok(marketDataService.getWidgetData(ticker));
  }

  @GetMapping("/quote/{ticker}")
  public ResponseEntity<?> getQuoteByTicker(@PathVariable String ticker) {
    try {
      String decodedTicker =
          java.net.URLDecoder.decode(ticker, java.nio.charset.StandardCharsets.UTF_8);
      QuoteData quote = quoteDataRepository.findById(decodedTicker).orElse(null);
      if (quote == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                Map.of(
                    "error", true, "message", "No quote data found for ticker: " + decodedTicker));
      }
      return ResponseEntity.ok(quote);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", true, "message", e.getMessage()));
    }
  }

  @GetMapping("/data/{ticker}")
  public ResponseEntity<?> getMarketDetailData(@PathVariable String ticker) {
    try {
      String decodedTicker =
          java.net.URLDecoder.decode(ticker, java.nio.charset.StandardCharsets.UTF_8);
      WidgetDataDto widgetData = marketDataService.getWidgetData(decodedTicker);
      if (widgetData == null || widgetData.getQuoteData() == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", true, "message", "Asset not found: " + decodedTicker));
      }
      return ResponseEntity.ok(widgetData);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", true, "message", e.getMessage()));
    }
  }

  @GetMapping("/top-gainers")
  public ResponseEntity<List<MarketData>> getTopGainers() {
    return ResponseEntity.ok(marketDataService.getTopGainers());
  }

  @GetMapping("/top-losers")
  public ResponseEntity<List<MarketData>> getTopLosers() {
    return ResponseEntity.ok(marketDataService.getTopLosers());
  }

  @GetMapping("/most-active")
  public ResponseEntity<List<MarketData>> getMostActive() {
    return ResponseEntity.ok(marketDataService.getMostActive());
  }

  @GetMapping("/historical/{ticker}")
  public ResponseEntity<?> getHistoricalData(@PathVariable String ticker) {
    try {
      Object data = marketDataService.fetchHistoricalData(ticker);
      return ResponseEntity.ok(data);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("error", true, "message", e.getMessage()));
    }
  }

  @PostMapping("/admin/refresh-movers")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<?> refreshMoversData() {
    try {
      marketDataService.fetchAndStoreMarketData("US", "MANUAL");
      return ResponseEntity.ok(
          Map.of("message", "Market movers data refresh triggered successfully."));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", true, "message", e.getMessage()));
    }
  }

  @PostMapping("/admin/refresh-indices")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<?> refreshIndicesData() {
    try {
      marketDataService.refreshIndices();
      return ResponseEntity.ok(
          Map.of("message", "Market indices data refresh triggered successfully."));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", true, "message", e.getMessage()));
    }
  }

  @PostMapping("/admin/flush-movers")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<?> flushMoversData(@RequestBody PasswordDto passwordDto) {
    try {
      marketDataService.flushMoversData(passwordDto.getPassword());
      return ResponseEntity.ok(Map.of("message", "Market movers data flushed successfully."));
    } catch (SecurityException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", true, "message", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", true, "message", e.getMessage()));
    }
  }

  @PostMapping("/admin/flush-indices")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<?> flushIndicesData(@RequestBody PasswordDto passwordDto) {
    try {
      marketDataService.flushIndicesData(passwordDto.getPassword());
      return ResponseEntity.ok(Map.of("message", "Market indices data flushed successfully."));
    } catch (SecurityException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", true, "message", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", true, "message", e.getMessage()));
    }
  }
}
