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
    public List<MarketData> fetchTopGainers() { return Collections.emptyList(); }
    @Override
    public List<MarketData> fetchTopLosers() { return Collections.emptyList(); }
    @Override
    public List<MarketData> fetchMostActive() { return Collections.emptyList(); }

    // Kept for legacy/other uses if needed
    @Override
    public Object fetchHistoricalData(String ticker) {
        String url = String.format("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s", ticker, apiKey);
        return restTemplate.getForObject(url, Object.class);
    }

    // --- NEW: Fetch and parse into our entity format for bridging gaps ---
    public List<HistoricalPrice> fetchDailyHistory(String ticker, boolean fullHistory) {
        String outputSize = fullHistory ? "full" : "compact"; // compact = 100 days, full = 20 years
        String url = String.format(
            "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&outputsize=%s&apikey=%s",
            ticker, outputSize, apiKey
        );

        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode timeSeries = root.path("Time Series (Daily)");

            if (timeSeries.isMissingNode()) {
                 // Check for API limits or errors
                 if (root.has("Note")) logger.warn("AlphaVantage API limit reached for {}", ticker);
                 else logger.error("Failed to fetch history for {}. Response: {}", ticker, jsonResponse);
                 return Collections.emptyList();
            }

            List<HistoricalPrice> history = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = timeSeries.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                HistoricalPrice hp = new HistoricalPrice();
                hp.setTicker(ticker);
                hp.setPriceDate(LocalDate.parse(entry.getKey()));
                // "4. close" is the standard closing price
                hp.setClosePrice(new BigDecimal(entry.getValue().get("4. close").asText()));
                history.add(hp);
            }
            return history;
        } catch (Exception e) {
            logger.error("Error parsing AlphaVantage history for {}: {}", ticker, e.getMessage());
            return Collections.emptyList();
        }
    }
}