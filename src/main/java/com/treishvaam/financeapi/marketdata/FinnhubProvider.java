package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NOTE: This provider is TEMPORARILY DISABLED.
 * Data is now being fetched by the Python script (market_data_updater.py).
 */
@Component("finnhubProvider")
public class FinnhubProvider {

    private static final Logger logger = LoggerFactory.getLogger(FinnhubProvider.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${finnhub.api.key}")
    private String apiKey;

    // --- DISABLED ---
    public QuoteData fetchQuote(String ticker) {
        logger.warn("FinnhubProvider.fetchQuote() is TEMPORARILY DISABLED. Python script handles this now.");
        // Re-throw or return null depending on how you want to handle total failure.
        throw new UnsupportedOperationException("FinnhubProvider is temporarily disabled. Data is fetched by Python script.");
    }

    // --- DISABLED ---
    public List<MarketHoliday> fetchMarketHolidays() {
        logger.warn("FinnhubProvider.fetchMarketHolidays() is TEMPORARILY DISABLED.");
        return Collections.emptyList();
    }

    // --- Helper Methods for Safe Parsing ---

    private BigDecimal safeGetDecimal(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            String text = node.get(fieldName).asText();
            // Finnhub sometimes returns "NaN" or empty strings
            if (text == null || text.isEmpty() || "NaN".equalsIgnoreCase(text) || "null".equalsIgnoreCase(text)) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO; // Default to 0 on parsing error
        }
    }

    private Long safeGetLong(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return 0L;
        }
        try {
            return node.get(fieldName).asLong(0L);
        } catch (Exception e) {
            return 0L;
        }
    }
}