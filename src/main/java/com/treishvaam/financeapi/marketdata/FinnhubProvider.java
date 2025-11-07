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
            // 1. Fetch Basic Quote (Price, Change, High, Low)
            String quoteJson = restTemplate.getForObject(url, String.class);
            JsonNode quoteNode = objectMapper.readTree(quoteJson);

            // 2. Fetch Advanced Metrics (Market Cap, P/E, Dividend, 52W High/Low)
            String metricJson = restTemplate.getForObject(metricUrl, String.class);
            JsonNode metricRoot = objectMapper.readTree(metricJson);
            JsonNode basicMetric = metricRoot.path("metric");

            QuoteData data = new QuoteData();
            data.setTicker(ticker);
            // From standard quote endpoint
            data.setCurrentPrice(new BigDecimal(quoteNode.get("c").asText()));
            data.setChangeAmount(new BigDecimal(quoteNode.get("d").asText()));
            data.setChangePercent(new BigDecimal(quoteNode.get("dp").asText()));
            data.setDayHigh(new BigDecimal(quoteNode.get("h").asText()));
            data.setDayLow(new BigDecimal(quoteNode.get("l").asText()));
            data.setOpenPrice(new BigDecimal(quoteNode.get("o").asText()));
            data.setPreviousClose(new BigDecimal(quoteNode.get("pc").asText()));

            // From metric endpoint
            if (!basicMetric.isMissingNode()) {
                 if (basicMetric.has("marketCapitalization")) {
                    // Finnhub returns Mkt Cap in Millions.
                    data.setMarketCap((long) (basicMetric.get("marketCapitalization").asDouble() * 1_000_000));
                }
                if (basicMetric.has("peBasicExclExtraTTM")) {
                     data.setPeRatio(new BigDecimal(basicMetric.get("peBasicExclExtraTTM").asText()));
                }
                 if (basicMetric.has("dividendYieldIndicatedAnnual")) {
                     data.setDividendYield(new BigDecimal(basicMetric.get("dividendYieldIndicatedAnnual").asText()));
                }
                 if (basicMetric.has("52WeekHigh")) {
                     data.setFiftyTwoWeekHigh(new BigDecimal(basicMetric.get("52WeekHigh").asText()));
                }
                 if (basicMetric.has("52WeekLow")) {
                     data.setFiftyTwoWeekLow(new BigDecimal(basicMetric.get("52WeekLow").asText()));
                }
            }

            data.setLastUpdated(LocalDateTime.now());
            return data;

        } catch (Exception e) {
            logger.error("Failed to fetch Finnhub quote for {}: {}", ticker, e.getMessage());
            throw new RuntimeException("Finnhub fetch failed for " + ticker, e);
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
            return new ArrayList<>(); // Return empty on failure to avoid startup crash
        }
    }
}