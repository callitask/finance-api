package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.apistatus.ApiFetchStatus;
import com.treishvaam.financeapi.apistatus.ApiFetchStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service("apiMarketDataService")
public class MarketDataService {

    private static final int CACHE_DURATION_MINUTES = 30;

    @Autowired
    private MarketDataRepository marketDataRepository;
    
    @Autowired
    private HistoricalDataCacheRepository historicalDataCacheRepository;

    @Autowired
    @Qualifier("apiMarketDataFactory")
    private MarketDataFactory marketDataFactory;

    @Autowired
    private ApiFetchStatusRepository apiFetchStatusRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @CacheEvict(value = {"top-gainers", "top-losers", "most-active"}, allEntries = true)
    @Transactional
    public void fetchAndStoreMarketData(String market, String triggerSource) {
        MarketDataProvider provider = marketDataFactory.getMoversProvider(market);
        String marketSuffix = " (" + market + ")";

        try {
            List<MarketData> gainers = provider.fetchTopGainers();
            if (gainers != null && !gainers.isEmpty()) {
                marketDataRepository.deleteByType("GAINER");
                marketDataRepository.saveAll(gainers);
            }
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Gainers" + marketSuffix, "SUCCESS", triggerSource, "Data fetched successfully."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Gainers" + marketSuffix, "FAILURE", triggerSource, e.getMessage()));
        }

        try {
            List<MarketData> losers = provider.fetchTopLosers();
            if (losers != null && !losers.isEmpty()) {
                marketDataRepository.deleteByType("LOSER");
                marketDataRepository.saveAll(losers);
            }
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Losers" + marketSuffix, "SUCCESS", triggerSource, "Data fetched successfully."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Losers" + marketSuffix, "FAILURE", triggerSource, e.getMessage()));
        }

        try {
            List<MarketData> active = provider.fetchMostActive();
            if (active != null && !active.isEmpty()) {
                marketDataRepository.deleteByType("ACTIVE");
                marketDataRepository.saveAll(active);
            }
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Most Active" + marketSuffix, "SUCCESS", triggerSource, "Data fetched successfully."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Most Active" + marketSuffix, "FAILURE", triggerSource, e.getMessage()));
        }
    }
   
    @Transactional
    public Object fetchHistoricalData(String ticker) {
        Optional<HistoricalDataCache> cachedDataOpt = historicalDataCacheRepository.findByTicker(ticker);

        if (cachedDataOpt.isPresent()) {
            HistoricalDataCache cachedData = cachedDataOpt.get();
            long minutesSinceFetch = ChronoUnit.MINUTES.between(cachedData.getLastFetched(), LocalDateTime.now());
            if (minutesSinceFetch < CACHE_DURATION_MINUTES) {
                try {
                    return objectMapper.readValue(cachedData.getData(), Object.class);
                } catch (JsonProcessingException e) {
                    // Fall through to fetch new data if parsing fails
                }
            }
        }

        MarketDataProvider provider = marketDataFactory.getHistoricalDataProvider();
        try {
            Object freshDataObject = provider.fetchHistoricalData(ticker);
            
            String freshDataJson = objectMapper.writeValueAsString(freshDataObject);
            HistoricalDataCache newCacheEntry = new HistoricalDataCache(ticker, freshDataJson, LocalDateTime.now());
            historicalDataCacheRepository.save(newCacheEntry);
            
            return freshDataObject;

        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Chart (" + ticker + ")", "FAILURE", "AUTOMATIC", e.getMessage()));
            throw new RuntimeException("Failed to fetch historical data for " + ticker + ": " + e.getMessage(), e);
        }
    }


    @Cacheable("top-gainers")
    public List<MarketData> getTopGainers() {
        return marketDataRepository.findByType("GAINER");
    }

    @Cacheable("top-losers")
    public List<MarketData> getTopLosers() {
        return marketDataRepository.findByType("LOSER");
    }

    @Cacheable("most-active")
    public List<MarketData> getMostActive() {
        return marketDataRepository.findByType("ACTIVE");
    }
}