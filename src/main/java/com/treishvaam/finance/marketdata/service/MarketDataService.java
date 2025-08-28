package com.treishvaam.finance.marketdata.service;

import com.treishvaam.finance.marketdata.entity.MarketData;
import com.treishvaam.finance.marketdata.provider.MarketDataFactory;
import com.treishvaam.finance.marketdata.provider.MarketDataProvider;
import com.treishvaam.finance.marketdata.repository.MarketDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class MarketDataService {

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    private MarketDataFactory marketDataFactory;

    public void fetchAndStoreMarketData(String market) {
        MarketDataProvider provider = marketDataFactory.getProvider(market);
        try {
            // Clear only the data for the specific market being updated
            marketDataRepository.deleteByType("GAINER");
            marketDataRepository.deleteByType("LOSER");
            marketDataRepository.deleteByType("ACTIVE");

            List<MarketData> gainers = provider.fetchTopGainers();
            List<MarketData> losers = provider.fetchTopLosers();
            List<MarketData> active = provider.fetchMostActive();

            marketDataRepository.saveAll(gainers);
            marketDataRepository.saveAll(losers);
            marketDataRepository.saveAll(active);
        } catch (Exception e) {
            System.err.println("Failed to fetch and store market data for market: " + market + ". Reason: " + e.getMessage());
            throw e;
        }
    }

    @Cacheable("top-gainers")
    public List<MarketData> getTopGainers() {
        List<MarketData> data = marketDataRepository.findByType("GAINER");
        return data != null ? data : Collections.emptyList();
    }

    @Cacheable("top-losers")
    public List<MarketData> getTopLosers() {
        List<MarketData> data = marketDataRepository.findByType("LOSER");
        return data != null ? data : Collections.emptyList();
    }

    @Cacheable("most-active")
    public List<MarketData> getMostActive() {
        List<MarketData> data = marketDataRepository.findByType("ACTIVE");
        return data != null ? data : Collections.emptyList();
    }
}