package com.treishvaam.finance.marketdata.controller;

import com.treishvaam.finance.marketdata.entity.MarketData;
import com.treishvaam.finance.marketdata.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    @Autowired
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

    // This is a new endpoint to manually trigger a data refresh for debugging
    @GetMapping("/refresh-us")
    public ResponseEntity<?> refreshUsMarketData() {
        try {
            marketDataService.fetchAndStoreMarketData("US");
            return ResponseEntity.ok(Map.of("message", "US market data refresh triggered successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }
    }
}