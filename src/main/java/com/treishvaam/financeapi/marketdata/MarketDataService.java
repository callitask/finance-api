package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.apistatus.ApiFetchStatus;
import com.treishvaam.financeapi.apistatus.ApiFetchStatusRepository;
import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void fetchAndStoreMarketData(String market, String triggerSource) {
        MarketDataProvider provider = marketDataFactory.getMoversProvider(market);
        String marketSuffix = " (" + market + ")";

        try {
            List<MarketData> gainers = provider.fetchTopGainers();
            marketDataRepository.deleteByType("GAINER");
            if (gainers != null && !gainers.isEmpty()) {
                marketDataRepository.saveAll(gainers);
            }
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Gainers" + marketSuffix, "SUCCESS", triggerSource, "Data fetched successfully."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Gainers" + marketSuffix, "FAILURE", triggerSource, e.getMessage()));
        }

        try {
            List<MarketData> losers = provider.fetchTopLosers();
            marketDataRepository.deleteByType("LOSER");
            if (losers != null && !losers.isEmpty()) {
                marketDataRepository.saveAll(losers);
            }
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Losers" + marketSuffix, "SUCCESS", triggerSource, "Data fetched successfully."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Losers" + marketSuffix, "FAILURE", triggerSource, e.getMessage()));
        }

        try {
            List<MarketData> active = provider.fetchMostActive();
            marketDataRepository.deleteByType("ACTIVE");
            if (active != null && !active.isEmpty()) {
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
                    // Fall through
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
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Chart (" + ticker + ")", "FAILURE", "MANUAL", e.getMessage()));
            throw new RuntimeException("Failed to fetch historical data for " + ticker + ": " + e.getMessage(), e);
        }
    }

    @Transactional
    public void refreshIndices() {
        List<String> indices = Arrays.asList("^GSPC", "^DJI", "^IXIC");
        for (String ticker : indices) {
            try {
                fetchHistoricalData(ticker);
                apiFetchStatusRepository.save(new ApiFetchStatus("Market Chart (" + ticker + ")", "SUCCESS", "MANUAL", "Data refreshed successfully."));
            } catch (Exception e) {
                // Error is logged inside fetchHistoricalData
            }
        }
    }

    @Transactional
    public void flushMoversData(String password) {
        if (!isPasswordValid(password)) {
            throw new SecurityException("Invalid password.");
        }
        marketDataRepository.deleteByType("GAINER");
        marketDataRepository.deleteByType("LOSER");
        marketDataRepository.deleteByType("ACTIVE");
    }

    @Transactional
    public void flushIndicesData(String password) {
        if (!isPasswordValid(password)) {
            throw new SecurityException("Invalid password.");
        }
        historicalDataCacheRepository.deleteAll();
    }

    private boolean isPasswordValid(String rawPassword) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in database"));
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public List<MarketData> getTopGainers() {
        return marketDataRepository.findByType("GAINER");
    }

    public List<MarketData> getTopLosers() {
        return marketDataRepository.findByType("LOSER");
    }

    public List<MarketData> getMostActive() {
        return marketDataRepository.findByType("ACTIVE");
    }
}