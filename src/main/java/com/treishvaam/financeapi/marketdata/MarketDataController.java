package com.treishvaam.financeapi.marketdata;

import com.treishvaam.financeapi.apistatus.PasswordDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {
    @Autowired
    @Qualifier("apiMarketDataService")
    private MarketDataService marketDataService;

    @PostMapping("/quotes/batch")
    public ResponseEntity<List<QuoteData>> getBatchQuotes(@RequestBody List<String> tickers) {
        return ResponseEntity.ok(marketDataService.getQuotesBatch(tickers));
    }

    // --- FIX: Changed from @PathVariable to @RequestParam to handle special chars like '^' safely ---
    // Old URL: /api/market/widget/^DJI (Blocked by Firewall/Server config sometimes)
    // New URL: /api/market/widget?ticker=^DJI (Allowed)
    @GetMapping("/widget")
    public ResponseEntity<WidgetDataDto> getWidgetData(@RequestParam String ticker) {
        return ResponseEntity.ok(marketDataService.getWidgetData(ticker));
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
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    @PostMapping("/admin/refresh-movers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> refreshMoversData() {
        try {
            marketDataService.fetchAndStoreMarketData("US", "MANUAL");
            return ResponseEntity.ok(Map.of("message", "Market movers data refresh triggered successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    @PostMapping("/admin/refresh-indices")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> refreshIndicesData() {
        try {
            marketDataService.refreshIndices();
            return ResponseEntity.ok(Map.of("message", "Market indices data refresh triggered successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    @PostMapping("/admin/flush-movers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> flushMoversData(@RequestBody PasswordDto passwordDto) {
        try {
            marketDataService.flushMoversData(passwordDto.getPassword());
            return ResponseEntity.ok(Map.of("message", "Market movers data flushed successfully."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", true, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    @PostMapping("/admin/flush-indices")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> flushIndicesData(@RequestBody PasswordDto passwordDto) {
        try {
            marketDataService.flushIndicesData(passwordDto.getPassword());
            return ResponseEntity.ok(Map.of("message", "Market indices data flushed successfully."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", true, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", true, "message", e.getMessage()));
        }
    }
}