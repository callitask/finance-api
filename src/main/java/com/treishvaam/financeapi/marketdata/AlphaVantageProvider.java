package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component("alphaVantageProvider")
public class AlphaVantageProvider implements MarketDataProvider {

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
        String function = ticker.startsWith("^") ? "TIME_SERIES_DAILY" : "TIME_SERIES_DAILY_ADJUSTED";
        // AlphaVantage expects symbols without the '^' prefix.
        String symbol = ticker.replace("^", ""); 

        String url = String.format(
            "https://www.alphavantage.co/query?function=%s&symbol=%s&apikey=%s",
            function,
            symbol,
            apiKey
        );

        // Fetch the response as a plain string to check for any issues first.
        String jsonResponse = restTemplate.getForObject(url, String.class);
        
        // This is a common issue with the free AlphaVantage API.
        if (jsonResponse != null && jsonResponse.contains("Note")) {
             throw new RuntimeException("API Note: This is likely a rate limit message from AlphaVantage.");
        }

        // If there are no obvious issues, parse and return the data.
        try {
            return objectMapper.readValue(jsonResponse, Object.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse the API response for " + ticker, e);
        }
    }
}