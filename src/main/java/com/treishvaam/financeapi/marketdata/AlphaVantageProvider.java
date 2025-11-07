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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component("alphaVantageProvider")
public class AlphaVantageProvider implements MarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(AlphaVantageProvider.class);

    @Value("${alphavantage.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<MarketData> fetchTopGainers() {
        return Collections.emptyList();
    }

    @Override
    public List<MarketData> fetchTopLosers() {
        return Collections.emptyList();
    }

    @Override
    public List<MarketData> fetchMostActive() {
        return Collections.emptyList();
    }

    @Override
    public Object fetchHistoricalData(String ticker) {
        // Kept for backward compatibility if needed by old components
        String url = String.format("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s", ticker, apiKey);
        return restTemplate.getForObject(url, Object.class);
    }

    // --- NEW: Fetch full history specifically for permanent DB seeding ---
    public List<HistoricalPrice> fetchPermanentHistory(String ticker) {
        // outputsize=full fetches 20+ years of data.
        String url = String.format(
            "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&outputsize=full&apikey=%s",
            ticker, apiKey
        );

        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode timeSeries = root.path("Time Series (Daily)");

            if (timeSeries.isMissingNode()) {
                logger.error("Failed to fetch full history for {}. API Response: {}", ticker, jsonResponse);
                return Collections.emptyList();
            }

            List<HistoricalPrice> history = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = timeSeries.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String dateStr = entry.getKey();
                JsonNode dataNode = entry.getValue();

                HistoricalPrice price = new HistoricalPrice();
                price.setTicker(ticker);
                price.setPriceDate(LocalDate.parse(dateStr));
                // "4. close" is the standard closing price field in AlphaVantage
                price.setClosePrice(new BigDecimal(dataNode.get("4. close").asText()));
                history.add(price);
            }
            return history;
        } catch (Exception e) {
            logger.error("Error parsing AlphaVantage full history for {}: {}", ticker, e.getMessage());
            throw new RuntimeException("AlphaVantage history fetch failed", e);
        }
    }
}