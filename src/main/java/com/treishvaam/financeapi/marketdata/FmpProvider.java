package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature; // --- IMPORT THIS ---
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component("apiFmpProvider")
public class FmpProvider implements MarketDataProvider {

    @Value("${fmp.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper; // We will configure this in the constructor

    // --- CONSTRUCTOR to configure ObjectMapper ---
    public FmpProvider() {
        this.objectMapper = new ObjectMapper();
        // This is the fix: tells the parser to not fail on unknown properties
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static final String FMP_BASE_URL = "https://financialmodelingprep.com/stable";

    @Override
    public List<MarketData> fetchTopGainers() {
        String url = FMP_BASE_URL + "/biggest-gainers?apikey=" + apiKey;
        return fetchData(url, "GAINER");
    }

    @Override
    public List<MarketData> fetchTopLosers() {
        String url = FMP_BASE_URL + "/biggest-losers?apikey=" + apiKey;
        return fetchData(url, "LOSER");
    }

    @Override
    public List<MarketData> fetchMostActive() {
        String url = FMP_BASE_URL + "/most-actives?apikey=" + apiKey;
        return fetchData(url, "ACTIVE");
    }

    private List<MarketData> fetchData(String url, String type) {
        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                System.err.println("Empty or null response from FMP API for URL: " + url);
                return Collections.emptyList();
            }

            if (!jsonResponse.trim().startsWith("[")) {
                throw new IOException("FMP API returned an error: " + jsonResponse);
            }

            List<FmpMarketDataDto> fmpData = objectMapper.readValue(jsonResponse, new TypeReference<List<FmpMarketDataDto>>() {});

            return fmpData.stream()
                    .map(dto -> {
                        MarketData marketData = new MarketData();
                        marketData.setTicker(dto.getSymbol());
                        marketData.setPrice(dto.getPrice());
                        marketData.setChangeAmount(dto.getChange());
                        marketData.setChangePercentage(String.format("%.2f%%", dto.getChangesPercentage()));
                        marketData.setType(type);
                        marketData.setLastUpdated(LocalDateTime.now());
                        return marketData;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch and parse data from FMP API at " + url, e);
        }
    }

    // This DTO no longer needs any changes
    private static class FmpMarketDataDto {
        public String symbol;
        public java.math.BigDecimal price;
        public java.math.BigDecimal change;
        public Double changesPercentage;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public java.math.BigDecimal getChange() { return change; }
        public void setChange(java.math.BigDecimal change) { this.change = change; }
        public Double getChangesPercentage() { return changesPercentage; }
        public void setChangesPercentage(Double changesPercentage) { this.changesPercentage = changesPercentage; }
    }
}