package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * NOTE: This provider is TEMPORARILY DISABLED. Data is now being fetched by the Python script
 * (market_data_updater.py). This class is kept for the legacy cache endpoint
 * /api/market/historical/{ticker}
 */
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

  // Kept for legacy /api/market/historical/{ticker} endpoint
  @Override
  public Object fetchHistoricalData(String ticker) {
    logger.warn("Using legacy fetchHistoricalData for {}. Python script is preferred.", ticker);
    String url =
        String.format(
            "https.www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
            ticker, apiKey);
    return restTemplate.getForObject(url, Object.class);
  }

  // --- DISABLED ---
  public List<HistoricalPrice> fetchDailyHistory(String ticker, boolean fullHistory) {
    logger.warn("fetchDailyHistory() is TEMPORARILY DISABLED. Python script handles this now.");
    return Collections.emptyList();
  }
}
