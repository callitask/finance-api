package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger; // IMPORTED
import org.slf4j.LoggerFactory; // IMPORTED
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("apiFmpProvider")
public class FmpProvider implements MarketDataProvider {

  // Added logger for better debugging
  private static final Logger logger = LoggerFactory.getLogger(FmpProvider.class);

  @Value("${fmp.api.key}")
  private String apiKey;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper;

  // Constructor to configure ObjectMapper (as in your example)
  public FmpProvider() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  // --- MODIFIED: Using your legacy "stable" base URL ---
  private static final String FMP_BASE_URL = "https://financialmodelingprep.com/stable";

  @Override
  public List<MarketData> fetchTopGainers() {
    // --- MODIFIED: Using your legacy "/biggest-gainers" endpoint ---
    String url = FMP_BASE_URL + "/biggest-gainers?apikey=" + apiKey;
    return fetchData(url, "GAINER");
  }

  @Override
  public List<MarketData> fetchTopLosers() {
    // --- MODIFIED: Using your legacy "/biggest-losers" endpoint ---
    String url = FMP_BASE_URL + "/biggest-losers?apikey=" + apiKey;
    return fetchData(url, "LOSER");
  }

  @Override
  public List<MarketData> fetchMostActive() {
    // --- MODIFIED: Using your legacy "/most-actives" endpoint ---
    String url = FMP_BASE_URL + "/most-actives?apikey=" + apiKey;
    return fetchData(url, "ACTIVE");
  }

  @Override
  public Object fetchHistoricalData(String ticker) {
    throw new UnsupportedOperationException(
        "FmpProvider does not support historical data fetching.");
  }

  // This fetchData method includes the robust logging
  private List<MarketData> fetchData(String url, String type) {
    try {
      String jsonResponse = restTemplate.getForObject(url, String.class);
      if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
        logger.warn("FMP API returned empty response for {}", url);
        return Collections.emptyList();
      }
      if (!jsonResponse.trim().startsWith("[")) {
        // This will log the actual error message
        logger.error("FMP API returned an error: {}", jsonResponse);
        throw new IOException("FMP API returned an error: " + jsonResponse);
      }
      List<FmpMarketDataDto> fmpData =
          objectMapper.readValue(jsonResponse, new TypeReference<List<FmpMarketDataDto>>() {});
      return fmpData.stream()
          .map(
              dto -> {
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
      // This will throw the full error, which MarketDataService will catch and log
      throw new RuntimeException("Failed to fetch and parse data from FMP API at " + url, e);
    }
  }

  // This DTO matches your code
  private static class FmpMarketDataDto {
    public String symbol;
    public String name;
    public java.math.BigDecimal price;
    public java.math.BigDecimal change;
    public Double changesPercentage;
    public Long volume;

    // Getters and Setters
    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public java.math.BigDecimal getPrice() {
      return price;
    }

    public void setPrice(java.math.BigDecimal price) {
      this.price = price;
    }

    public java.math.BigDecimal getChange() {
      return change;
    }

    public void setChange(java.math.BigDecimal change) {
      this.change = change;
    }

    public Double getChangesPercentage() {
      return changesPercentage;
    }

    public void setChangesPercentage(Double changesPercentage) {
      this.changesPercentage = changesPercentage;
    }

    public Long getVolume() {
      return volume;
    }

    public void setVolume(Long volume) {
      this.volume = volume;
    }
  }
}
