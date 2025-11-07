package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.apistatus.ApiFetchStatus;
import com.treishvaam.financeapi.apistatus.ApiFetchStatusRepository;
import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service("apiMarketDataService")
public class MarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private static final int CACHE_DURATION_MINUTES = 30;
    private static final List<String> SUPPORTED_ETFS = Arrays.asList("SPY", "DIA", "QQQ");

    @Autowired
    private MarketDataRepository marketDataRepository;
    @Autowired
    private HistoricalDataCacheRepository historicalDataCacheRepository;
    @Autowired
    private QuoteDataRepository quoteDataRepository;
    @Autowired
    private HistoricalPriceRepository historicalPriceRepository;
    @Autowired
    private MarketHolidayRepository marketHolidayRepository;
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

    @PostConstruct
    public void startupWarmup() {
        new Thread(() -> {
            logger.info("STARTUP: Initializing Market Data...");
            initializeHolidays();
            // Fetch supported ETFs immediately on startup so home page works
            for (String ticker : SUPPORTED_ETFS) {
                logger.info("STARTUP: Warming up data for {}", ticker);
                getWidgetData(ticker); // This will trigger fetch if missing
            }
            logger.info("STARTUP: Market Data warmup complete.");
        }).start();
    }

    public void initializeHolidays() {
        if (marketHolidayRepository.count() == 0) {
             try {
                 List<MarketHoliday> holidays = marketDataFactory.getQuoteProvider().fetchMarketHolidays();
                 if (!holidays.isEmpty()) {
                     marketHolidayRepository.saveAll(holidays);
                 }
             } catch (Exception e) {
                 logger.error("Failed to initialize holidays: {}", e.getMessage());
             }
        }
    }

    // --- Consolidated Widget Data Fetch ---
    public WidgetDataDto getWidgetData(String ticker) {
        // 1. Get or Fetch Quote
        QuoteData quote = quoteDataRepository.findById(ticker).orElse(null);
        if (quote == null) {
             try {
                 quote = marketDataFactory.getQuoteProvider().fetchQuote(ticker);
                 quoteDataRepository.save(quote);
             } catch (Exception e) {
                 logger.error("Error auto-fetching quote for {}: {}", ticker, e.getMessage());
             }
        }

        // 2. Get or Fetch History
        List<HistoricalPrice> history = historicalPriceRepository.findByTickerOrderByPriceDateAsc(ticker);
        if (history.isEmpty()) {
            try {
                // If history is empty, try to fetch it now (blocking, but necessary for first load)
                updateDailyHistory(ticker, "AUTO-FETCH");
                history = historicalPriceRepository.findByTickerOrderByPriceDateAsc(ticker);
            } catch (Exception e) {
                 logger.error("Error auto-fetching history for {}: {}", ticker, e.getMessage());
            }
        }
        return new WidgetDataDto(quote, history);
    }

    @Transactional
    public void updateAllQuotes(String triggerSource) {
        for (String ticker : SUPPORTED_ETFS) {
            try {
                QuoteData data = marketDataFactory.getQuoteProvider().fetchQuote(ticker);
                quoteDataRepository.save(data);
            } catch (Exception e) {
                apiFetchStatusRepository.save(new ApiFetchStatus("Quote Update (" + ticker + ")", "FAILURE", triggerSource, e.getMessage()));
            }
        }
    }

    // --- NEW: Fetch and append daily history from AlphaVantage ---
    @Transactional
    public void updateDailyHistory(String ticker, String triggerSource) {
        try {
            // We use the existing AlphaVantage provider but cast generic Object to expected format manually here
            // or ideally, update AlphaVantageProvider to return a better structure.
            // For strict adherence to your "no other functionality changed" rule, I'll parse the raw object here if needed,
            // BUT it's safer to let AlphaVantage return the raw map and we parse it.
            // Assuming fetchHistoricalData returns the Jackson JsonNode or Map structure:
            Object rawData = marketDataFactory.getHistoricalDataProvider().fetchHistoricalData(ticker);
            String json = objectMapper.writeValueAsString(rawData);
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode timeSeries = root.path("Time Series (Daily)");

            List<HistoricalPrice> newPrices = new ArrayList<>();
            Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = timeSeries.fields();
            while (fields.hasNext()) {
                Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry = fields.next();
                LocalDate date = LocalDate.parse(entry.getKey(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                // Only add if it doesn't exist to avoid unique constraint violations
                if (!historicalPriceRepository.existsByTickerAndPriceDate(ticker, date)) {
                     BigDecimal close = new BigDecimal(entry.getValue().get("4. close").asText());
                     newPrices.add(new HistoricalPrice(ticker, date, close));
                }
            }
            if (!newPrices.isEmpty()) {
                historicalPriceRepository.saveAll(newPrices);
            }
            apiFetchStatusRepository.save(new ApiFetchStatus("History Update (" + ticker + ")", "SUCCESS", triggerSource, "Added " + newPrices.size() + " records."));
        } catch (Exception e) {
            logger.error("Failed to update history for {}: {}", ticker, e.getMessage());
            apiFetchStatusRepository.save(new ApiFetchStatus("History Update (" + ticker + ")", "FAILURE", triggerSource, e.getMessage()));
            throw new RuntimeException(e);
        }
    }


    // ================= EXISTING METHODS (Legacy Support) =================

    @Transactional
    public void fetchAndStoreMarketData(String market, String triggerSource) {
        MarketDataProvider provider = marketDataFactory.getMoversProvider(market);
        String marketSuffix = " (" + market + ")";
        try {
            List<MarketData> gainers = provider.fetchTopGainers();
            marketDataRepository.deleteByType("GAINER");
            if (gainers != null && !gainers.isEmpty()) marketDataRepository.saveAll(gainers);
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Gainers" + marketSuffix, "SUCCESS", triggerSource, "Data fetched successfully."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Gainers" + marketSuffix, "FAILURE", triggerSource, e.getMessage()));
        }
        try {
            List<MarketData> losers = provider.fetchTopLosers();
            marketDataRepository.deleteByType("LOSER");
            if (losers != null && !losers.isEmpty()) marketDataRepository.saveAll(losers);
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Losers" + marketSuffix, "SUCCESS", triggerSource, "Data fetched successfully."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Data - Top Losers" + marketSuffix, "FAILURE", triggerSource, e.getMessage()));
        }
        try {
            List<MarketData> active = provider.fetchMostActive();
            marketDataRepository.deleteByType("ACTIVE");
            if (active != null && !active.isEmpty()) marketDataRepository.saveAll(active);
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
                } catch (JsonProcessingException e) { }
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
        for (String ticker : SUPPORTED_ETFS) {
            try {
                // Also refresh the new widget data when admin manually refreshes indices
                getWidgetData(ticker);
                fetchHistoricalData(ticker); // Keep old cache slightly matched for safety
                apiFetchStatusRepository.save(new ApiFetchStatus("Market Chart (" + ticker + ")", "SUCCESS", "MANUAL", "Data refreshed successfully."));
            } catch (Exception e) { }
        }
    }

    @Transactional
    public void flushMoversData(String password) {
        if (!isPasswordValid(password)) throw new SecurityException("Invalid password.");
        marketDataRepository.deleteByType("GAINER");
        marketDataRepository.deleteByType("LOSER");
        marketDataRepository.deleteByType("ACTIVE");
    }

    @Transactional
    public void flushIndicesData(String password) {
        if (!isPasswordValid(password)) throw new SecurityException("Invalid password.");
        historicalDataCacheRepository.deleteAll();
    }

    private boolean isPasswordValid(String rawPassword) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow(() -> new RuntimeException("User not found"));
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public List<MarketData> getTopGainers() { return marketDataRepository.findByType("GAINER"); }
    public List<MarketData> getTopLosers() { return marketDataRepository.findByType("LOSER"); }
    public List<MarketData> getMostActive() { return marketDataRepository.findByType("ACTIVE"); }
}