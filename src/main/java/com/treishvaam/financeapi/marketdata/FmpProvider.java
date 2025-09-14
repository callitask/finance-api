package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
   private final ObjectMapper objectMapper;

   public FmpProvider() {
       this.objectMapper = new ObjectMapper();
       this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
   }

   private static final String FMP_API_URL = "https://financialmodelingprep.com/api/v3";

   @Override
   public List<MarketData> fetchTopGainers() {
       String url = FMP_API_URL + "/stock_market/gainers?apikey=" + apiKey;
       return fetchData(url, "GAINER");
   }

   @Override
   public List<MarketData> fetchTopLosers() {
       String url = FMP_API_URL + "/stock_market/losers?apikey=" + apiKey;
       return fetchData(url, "LOSER");
   }

   @Override
   public List<MarketData> fetchMostActive() {
       String url = FMP_API_URL + "/stock_market/actives?apikey=" + apiKey;
       return fetchData(url, "ACTIVE");
   }

   // --- MODIFICATION: This provider does not support historical data ---
   @Override
   public Object fetchHistoricalData(String ticker) {
       throw new UnsupportedOperationException("FmpProvider does not support historical data fetching.");
   }

   private List<MarketData> fetchData(String url, String type) {
       // ... (rest of the method is unchanged)
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
                       marketData.setName(dto.getName());
                       marketData.setPrice(dto.getPrice());
                       marketData.setChangeAmount(dto.getChange());
                       marketData.setChangePercentage(String.format("%.2f%%", dto.getChangesPercentage()));
                       marketData.setVolume(dto.getVolume());
                       marketData.setType(type);
                       marketData.setLastUpdated(LocalDateTime.now());
                       return marketData;
                   })
                   .collect(Collectors.toList());
       } catch (Exception e) {
           throw new RuntimeException("Failed to fetch and parse data from FMP API at " + url, e);
       }
   }

   private static class FmpMarketDataDto {
       public String symbol;
       public String name;
       public java.math.BigDecimal price;
       public java.math.BigDecimal change;
       public Double changesPercentage;
       public Long volume;
       // Getters and Setters ...
       public String getSymbol() { return symbol; }
       public void setSymbol(String symbol) { this.symbol = symbol; }
       public String getName() { return name; }
       public void setName(String name) { this.name = name; }
       public java.math.BigDecimal getPrice() { return price; }
       public void setPrice(java.math.BigDecimal price) { this.price = price; }
       public java.math.BigDecimal getChange() { return change; }
       public void setChange(java.math.BigDecimal change) { this.change = change; }
       public Double getChangesPercentage() { return changesPercentage; }
       public void setChangesPercentage(Double changesPercentage) { this.changesPercentage = changesPercentage; }
       public Long getVolume() { return volume; }
       public void setVolume(Long volume) { this.volume = volume; }
   }
}