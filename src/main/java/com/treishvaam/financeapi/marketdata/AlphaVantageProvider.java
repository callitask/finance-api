package com.treishvaam.financeapi.marketdata;

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
        String symbol = ticker.startsWith("^") ? ticker.substring(1) : ticker; 

        String url = String.format(
            "https://www.alphavantage.co/query?function=%s&symbol=%s&apikey=%s",
            function,
            symbol,
            apiKey
        );
        return restTemplate.getForObject(url, Object.class);
    }
}