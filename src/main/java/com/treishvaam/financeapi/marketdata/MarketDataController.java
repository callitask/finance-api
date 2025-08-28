package com.treishvaam.financeapi.marketdata;

import com.treishvaam.financeapi.marketdata.MarketData;
import com.treishvaam.financeapi.marketdata.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    @Autowired
    private MarketDataService marketDataService;

    @Value("${fmp.api.key}")
    private String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();

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

    @GetMapping("/refresh-us")
    public ResponseEntity<?> refreshUsMarketData() {
        try {
            marketDataService.fetchAndStoreMarketData("US");
            return ResponseEntity.ok(Map.of("message", "US market data refresh triggered successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }
    }

    // --- FIXED DEBUGGING ENDPOINT ---
    @GetMapping("/test-fmp")
    public ResponseEntity<?> testFmpEndpoint(@RequestParam String path) {
        // --- FIXED: Updated to use the correct v3 base URL ---
        String url = "https://financialmodelingprep.com/api/v3/" + path + "?apikey=" + apiKey;
        try {
            Object response = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch from FMP: " + e.getMessage(), "url", url));
        }
    }
}