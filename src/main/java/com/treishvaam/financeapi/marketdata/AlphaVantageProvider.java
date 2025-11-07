package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
// REMOVED: import java.util.Map;

@Component("alphaVantageProvider")
public class AlphaVantageProvider implements MarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(AlphaVantageProvider.class);

    @Value("${alphavantage.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // REMOVED: static TICKER_MAP

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
        // CHANGED: Use ticker directly.
        String apiSymbol = ticker; 
        String function = "TIME_SERIES_DAILY";

        String url = String.format(
            "https://www.alphavantage.co/query?function=%s&symbol=%s&apikey=%s",
            function,
            apiSymbol,
            apiKey
        );

        String jsonResponse = restTemplate.getForObject(url, String.class);
        logger.info("Alpha Vantage Response for [{}]: {}", ticker, jsonResponse);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (rootNode.has("Error Message")) {
                throw new RuntimeException("Invalid data format received for " + ticker + ". API says: " + rootNode.get("Error Message").asText());
            }

            if (rootNode.has("Note")) {
                throw new RuntimeException("API rate limit likely exceeded for " + ticker + ". API says: " + rootNode.get("Note").asText());
            }

            if (!rootNode.has("Time Series (Daily)")) {
                 throw new RuntimeException("Invalid data format received for " + ticker + ". API did not return valid time series data.");
            }

            return objectMapper.treeToValue(rootNode, Object.class);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse the API response for " + ticker, e);
        }
    }
}