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
import java.util.List;

@Component("finnhubProvider")
public class FinnhubProvider {

    private static final Logger logger = LoggerFactory.getLogger(FinnhubProvider.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${finnhub.api.key}")
    private String apiKey;

    public QuoteData fetchQuote(String ticker) {
        String url = "https://finnhub.io/api/v1/quote?symbol=" + ticker + "&token=" + apiKey;
        String metricUrl = "https://finnhub.io/api/v1/stock/metric?symbol=" + ticker + "&metric=all&token=" + apiKey;

        try {
            // 1. Fetch Basic Quote
            String quoteJson = restTemplate.getForObject(url, String.class);
            JsonNode quoteNode = objectMapper.readTree(quoteJson);

            // 2. Fetch Advanced Metrics
            String metricJson = restTemplate.getForObject(metricUrl, String.class);
            JsonNode metricRoot = objectMapper.readTree(metricJson);
            JsonNode basicMetric = metricRoot.path("metric");

            QuoteData data = new QuoteData();
            data.setTicker(ticker);

            // Use safe getters to prevent crashes on null/'N/A'
            data.setCurrentPrice(safeGetDecimal(quoteNode, "c"));
            data.setChangeAmount(safeGetDecimal(quoteNode, "d"));
            data.setChangePercent(safeGetDecimal(quoteNode, "dp"));
            data.setDayHigh(safeGetDecimal(quoteNode, "h"));
            data.setDayLow(safeGetDecimal(quoteNode, "l"));
            data.setOpenPrice(safeGetDecimal(quoteNode, "o"));
            data.setPreviousClose(safeGetDecimal(quoteNode, "pc"));

            if (!basicMetric.isMissingNode()) {
                 data.setMarketCap(safeGetLong(basicMetric, "marketCapitalization") * 1_000_000);
                 data.setPeRatio(safeGetDecimal(basicMetric, "peBasicExclExtraTTM"));
                 data.setDividendYield(safeGetDecimal(basicMetric, "dividendYieldIndicatedAnnual"));
                 data.setFiftyTwoWeekHigh(safeGetDecimal(basicMetric, "52WeekHigh"));
                 data.setFiftyTwoWeekLow(safeGetDecimal(basicMetric, "52WeekLow"));
            }

            data.setLastUpdated(LocalDateTime.now());
            return data;

        } catch (Exception e) {
            logger.error("Failed to fetch Finnhub quote for {}: {}", ticker, e.getMessage());
            // Re-throw or return null depending on how you want to handle total failure.
            // For now, re-throwing so the service knows it failed.
            throw new RuntimeException("Finnhub fetch failed for " + ticker + ": " + e.getMessage(), e);
        }
    }

    public List<MarketHoliday> fetchMarketHolidays() {
        String url = "https://finnhub.io/api/v1/stock/market-holiday?exchange=US&token=" + apiKey;
        List<MarketHoliday> holidays = new ArrayList<>();
        try {
             String jsonResponse = restTemplate.getForObject(url, String.class);
             JsonNode root = objectMapper.readTree(jsonResponse);
             if (root.has("data")) {
                 for (JsonNode node : root.get("data")) {
                     MarketHoliday holiday = new MarketHoliday();
                     holiday.setHolidayDate(LocalDate.parse(node.get("atDate").asText()));
                     holiday.setEventName(node.get("eventName").asText());
                     holiday.setMarket("US");
                     holidays.add(holiday);
                 }
             }
             return holidays;
        } catch (Exception e) {
            logger.error("Failed to fetch Finnhub holidays: {}", e.getMessage());
            return new ArrayList<>();
        }
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