package com.treishvaam.financeapi.marketdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("apiMarketDataController")
@RequestMapping("/api/market")
public class MarketDataController {

    @Autowired
    @Qualifier("apiMarketDataService")
    private MarketDataService marketDataService;

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
            // THIS IS THE FIX: Send a proper error status code (503)
            // This tells the frontend that something actually went wrong.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    @PostMapping("/admin/refresh-us")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> refreshUsMarketData() {
        try {
            marketDataService.fetchAndStoreMarketData("US", "MANUAL");
            return ResponseEntity.ok(Map.of("message", "US market data refresh triggered successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", true, "message", e.getMessage()));
        }
    }
}