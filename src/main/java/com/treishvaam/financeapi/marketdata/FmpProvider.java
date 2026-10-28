/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Financial Modeling Prep (FMP) market data provider implementation.
 *
 * <p>Scope: - Fetches gainers, losers, and most-active market data from FMP and maps to MarketData.
 *
 * <p>Critical Dependencies: - Backend: MarketDataService, RestTemplate, ObjectMapper.
 *
 * <p>Security Constraints: - MARKET_DATA_API_KEY must never appear in logs or exception messages
 * (CWE-209/CWE-532). - All URLs containing apikey must be redacted via redactSecrets before
 * logging/throwing.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Security Remediation - API Key Redaction -
 * 2026-10-27): Added redactSecrets helper and applied it to all log parameters and exception
 * messages in fetchData. Why: Prevents MARKET_DATA_API_KEY leakage via logs, DB rows, and HTTP
 * error responses while preserving @CircuitBreaker behavior.
 *
 * <p>- DO-NOT-DELETE RULE (ABSOLUTE): This IMMUTABLE CHANGE HISTORY section acts as the
 * institutional memory for future AI sessions. It must never be deleted, truncated, rewritten, or
 * regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("apiFmpProvider")
public class FmpProvider implements MarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(FmpProvider.class);

    @Value("${fmp.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public FmpProvider() {
        this.objectMapper = new ObjectMapper();
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

    @Override
    public Object fetchHistoricalData(String ticker) {
        throw new UnsupportedOperationException(
                "FmpProvider does not support historical data fetching.");
    }

    private String redactSecrets(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = input.replaceAll("(?i)(apikey=)[^&\\s]+", "$1[REDACTED]");
        if (apiKey != null && !apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED]");
        }
        return sanitized;
    }

    private List<MarketData> fetchData(String url, String type) {
        String safeUrl = redactSecrets(url);
        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                logger.warn("FMP API returned empty response for {}", safeUrl);
                return Collections.emptyList();
            }

            if (!jsonResponse.trim().startsWith("[")) {
                String safeResponse = redactSecrets(jsonResponse);
                logger.error("FMP API returned an error for {}: {}", safeUrl, safeResponse);
                throw new IOException("FMP API returned an upstream error payload.");
            }

            List<FmpMarketDataDto> fmpData =
                    objectMapper.readValue(
                            jsonResponse, new TypeReference<List<FmpMarketDataDto>>() {});

            return fmpData.stream()
                    .map(
                            dto -> {
                                MarketData marketData = new MarketData();
                                marketData.setTicker(dto.getSymbol());
                                marketData.setName(dto.getName());
                                marketData.setPrice(dto.getPrice());
                                marketData.setChangeAmount(dto.getChange());
                                marketData.setChangePercentage(
                                        String.format("%.2f%%", dto.getChangesPercentage()));
                                marketData.setVolume(dto.getVolume());
                                marketData.setType(type);
                                marketData.setLastUpdated(LocalDateTime.now());
                                return marketData;
                            })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            String safeMessage = redactSecrets(e.getMessage());
            logger.error("Failed to fetch data from FMP API at {}: {}", safeUrl, safeMessage);
            throw new RuntimeException(
                    "Failed to fetch and parse data from FMP API at "
                            + safeUrl
                            + ": "
                            + safeMessage);
        }
    }

    private static class FmpMarketDataDto {
        public String symbol;
        public String name;
        public java.math.BigDecimal price;
        public java.math.BigDecimal change;
        public Double changesPercentage;
        public Long volume;

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
