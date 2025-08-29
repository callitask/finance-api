package com.treishvaam.financeapi.marketdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service("apiMarketDataService")
public class MarketDataService {

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    @Qualifier("apiMarketDataFactory")
    private MarketDataFactory marketDataFactory;

    @CacheEvict(value = {"top-gainers", "top-losers", "most-active"}, allEntries = true)
    @Transactional
    public void fetchAndStoreMarketData(String market) {
        MarketDataProvider provider = marketDataFactory.getProvider(market);
        try {
            List<MarketData> gainers = provider.fetchTopGainers();
            List<MarketData> losers = provider.fetchTopLosers();
            // --- CORRECTED: Fixed the typo in the method name ---
            List<MarketData> active = provider.fetchMostActive();

            if (gainers != null && !gainers.isEmpty()) {
                marketDataRepository.deleteByType("GAINER");
                marketDataRepository.saveAll(gainers);
            }

            if (losers != null && !losers.isEmpty()) {
                marketDataRepository.deleteByType("LOSER");
                marketDataRepository.saveAll(losers);
            }

            if (active != null && !active.isEmpty()) {
                marketDataRepository.deleteByType("ACTIVE");
                marketDataRepository.saveAll(active);
            }

        } catch (Exception e) {
            System.err.println("An error occurred during market data fetch for market: " + market);
            e.printStackTrace();
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