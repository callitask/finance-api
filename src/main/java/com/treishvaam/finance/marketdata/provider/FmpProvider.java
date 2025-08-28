package com.treishvaam.finance.marketdata.provider;

import com.treishvaam.finance.marketdata.entity.MarketData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component("fmpProvider")
public class FmpProvider implements MarketDataProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fmp.api.key}")
    private String apiKey;

    private static final String BASE_URL = "https://financialmodelingprep.com/api/v3/";

    @Override
    public List<MarketData> fetchTopGainers() {
        String url = BASE_URL + "stock_market/gainers?apikey=" + apiKey;
        return fetchData(url, "GAINER");
    }

    @Override
    public List<MarketData> fetchTopLosers() {
        String url = BASE_URL + "stock_market/losers?apikey=" + apiKey;
        return fetchData(url, "LOSER");
    }

    @Override
    public List<MarketData> fetchMostActive() {
        String url = BASE_URL + "stock_market/actives?apikey=" + apiKey;
        return fetchData(url, "ACTIVE");
    }

    private List<MarketData> fetchData(String url, String type) {
        try {
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
            List<MarketData> marketDataList = new ArrayList<>();

            if (response != null) {
                for (Map<String, Object> item : response) {
                    MarketData md = new MarketData();
                    md.setTicker((String) item.get("symbol"));
                    md.setPrice(new BigDecimal(item.get("price").toString()));
                    md.setChangeAmount(new BigDecimal(item.get("change").toString()));
                    md.setChangePercentage(String.format("%.2f%%", (Double) item.get("changesPercentage")));
                    md.setType(type);
                    md.setLastUpdated(LocalDateTime.now());
                    marketDataList.add(md);
                }
            }
            return marketDataList;
        } catch (HttpClientErrorException e) {
            System.err.println("Error fetching data from FMP. Status: " + e.getStatusCode());
            System.err.println("Response Body: " + e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch from FMP: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            System.err.println("A general error occurred while fetching from FMP: " + e.getMessage());
            throw new RuntimeException("An unexpected error occurred: " + e.getMessage(), e);
        }
    }
}