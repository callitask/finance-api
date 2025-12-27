package com.treishvaam.financeapi.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("yahooHistoricalProvider")
public class YahooHistoricalProvider {

  private static final Logger logger = LoggerFactory.getLogger(YahooHistoricalProvider.class);
  private final RestTemplate restTemplate = new RestTemplate();

  public List<HistoricalPrice> fetchFullHistory(String ticker) {
    long endTime = System.currentTimeMillis() / 1000;
    long startTime = endTime - (20L * 365 * 24 * 60 * 60);

    String url =
        String.format(
            "https://query1.finance.yahoo.com/v7/finance/download/%s?period1=%d&period2=%d&interval=1d&events=history&includeAdjustedClose=true",
            ticker, startTime, endTime);

    try {
      // --- FIX: Add User-Agent header to look like a real browser ---
      HttpHeaders headers = new HttpHeaders();
      headers.set(
          "User-Agent",
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      String csvResponse = response.getBody();

      if (csvResponse == null || csvResponse.isEmpty()) {
        return Collections.emptyList();
      }

      List<HistoricalPrice> history = new ArrayList<>();
      String[] rows = csvResponse.split("\n");

      for (int i = 1; i < rows.length; i++) {
        try {
          String[] cols = rows[i].split(",");
          if (cols.length < 6) continue;
          String dateStr = cols[0];
          String priceStr = cols[4];
          if ("null".equals(priceStr)) continue;

          HistoricalPrice price = new HistoricalPrice();
          price.setTicker(ticker);
          price.setPriceDate(LocalDate.parse(dateStr));
          price.setClosePrice(new BigDecimal(priceStr));
          history.add(price);
        } catch (Exception e) {
          // Skip bad rows
        }
      }
      return history;
    } catch (Exception e) {
      logger.error("Failed to fetch Yahoo history for {}: {}", ticker, e.getMessage());
      return Collections.emptyList();
    }
  }
}
