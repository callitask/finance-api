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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("apiMarketDataService")
public class MarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private static final int CACHE_DURATION_MINUTES = 30;

    private static final List<String> SUPPORTED_ETFS = Arrays.asList(
            "SPY", "DIA", "QQQ", "IWM", "VTI", "VIXY", "EWG", "EWU", "EWH"
    );

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
    // Explicitly inject AlphaVantageProvider for the new bridge functionality
    @Autowired
    @Qualifier("alphaVantageProvider")
    private AlphaVantageProvider alphaVantageProvider;
    @Autowired
    private ApiFetchStatusRepository apiFetchStatusRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CsvHistoryLoader csvHistoryLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initializeData() {
        logger.info("STARTUP: Initializing Market Data Service...");
        initializeHolidays();
        // 1. Load standard 30yr history from CSV
        csvHistoryLoader.loadCsvIfEmpty();
        
        // 2. Start async tasks: bridge any data gaps AND get fresh live quotes
        new Thread(() -> {
            bridgeDataGap(); // New method to fill 2023-2025 gap
            updateAllQuotes("STARTUP");
        }).start();
    }

    private void bridgeDataGap() {
        logger.info("Checking for historical data gaps to bridge...");
        LocalDate today = LocalDate.now();
        
        for (String ticker : SUPPORTED_ETFS) {
            try {
                Optional<HistoricalPrice> lastEntry = historicalPriceRepository.findTopByTickerOrderByPriceDateDesc(ticker);
                // If no data, or data is older than 5 days, fetch recent history
                if (lastEntry.isEmpty() || ChronoUnit.DAYS.between(lastEntry.get().getPriceDate(), today) > 5) {
                    logger.info("Bridging data gap for {}. Last date: {}", ticker, lastEntry.map(HistoricalPrice::getPriceDate).orElse(null));
                    
                    // Fetch full history if completely empty, otherwise compact (last 100 days) might be enough if gap is small.
                    // Safest to use 'full' if gap is > 100 days.
                    boolean fetchFull = lastEntry.isEmpty() || ChronoUnit.DAYS.between(lastEntry.get().getPriceDate(), today) > 90;
                    
                    List<HistoricalPrice> recentHistory = alphaVantageProvider.fetchDailyHistory(ticker, fetchFull);
                    
                    // Filter only NEW records that are AFTER our last stored date
                    LocalDate lastDate = lastEntry.map(HistoricalPrice::getPriceDate).orElse(LocalDate.MIN);
                    List<HistoricalPrice> newRecords = recentHistory.stream()
                            .filter(hp -> hp.getPriceDate().isAfter(lastDate))
                            .collect(Collectors.toList());

                    if (!newRecords.isEmpty()) {
                        historicalPriceRepository.saveAll(newRecords);
                        logger.info("Bridged gap for {}: Inserted {} new daily records.", ticker, newRecords.size());
                    } else {
                         logger.info("No new records found to bridge for {} (API might be delayed).", ticker);
                    }
                    
                    // Sleep to respect API rate limits (approx 12s between calls = 5 calls/min)
                    Thread.sleep(12500); 
                }
            } catch (Exception e) {
                logger.error("Failed to bridge data gap for {}: {}", ticker, e.getMessage());
            }
        }
        logger.info("Data gap bridging complete.");
    }

    private void initializeHolidays() {
        if (marketHolidayRepository.count() == 0) {
             try {
                 List<MarketHoliday> holidays = marketDataFactory.getQuoteProvider().fetchMarketHolidays();
                 if (!holidays.isEmpty()) {
                     marketHolidayRepository.saveAll(holidays);
                     logger.info("Initialized {} market holidays.", holidays.size());
                 }
             } catch (Exception e) {
                 logger.error("Failed to initialize market holidays: {}", e.getMessage());
             }
        }
    }

    public WidgetDataDto getWidgetData(String ticker) {
        QuoteData quote = quoteDataRepository.findById(ticker).orElse(null);
        if (quote == null) {
             try {
                 quote = marketDataFactory.getQuoteProvider().fetchQuote(ticker);
                 quoteDataRepository.save(quote);
             } catch (Exception e) {
                 logger.error("Immediate quote fetch failed for {}: {}", ticker, e.getMessage());
             }
        }
        List<HistoricalPrice> history = historicalPriceRepository.findByTickerOrderByPriceDateAsc(ticker);
        return new WidgetDataDto(quote, history);
    }

    @Transactional
    public void updateAllQuotes(String triggerSource) {
        logger.info("Starting updateAllQuotes from source: {}", triggerSource);
        int successCount = 0;
        for (String ticker : SUPPORTED_ETFS) {
            try {
                QuoteData data = marketDataFactory.getQuoteProvider().fetchQuote(ticker);
                quoteDataRepository.save(data);
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to update quote for {}: {}", ticker, e.getMessage());
                apiFetchStatusRepository.save(new ApiFetchStatus("Quote Update (" + ticker + ")", "FAILURE", triggerSource, e.getMessage()));
            }
        }
        if (successCount > 0) {
             apiFetchStatusRepository.save(new ApiFetchStatus("All Quotes Update", "SUCCESS", triggerSource, "Updated " + successCount + " quotes."));
        }
    }

    // ================= EXISTING METHODS BELOW =================

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
            if (ChronoUnit.MINUTES.between(cachedData.getLastFetched(), LocalDateTime.now()) < CACHE_DURATION_MINUTES) {
                try { return objectMapper.readValue(cachedData.getData(), Object.class); } catch (JsonProcessingException e) {}
            }
        }
        try {
            Object freshData = marketDataFactory.getHistoricalDataProvider().fetchHistoricalData(ticker);
            historicalDataCacheRepository.save(new HistoricalDataCache(ticker, objectMapper.writeValueAsString(freshData), LocalDateTime.now()));
            return freshData;
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Chart (" + ticker + ")", "FAILURE", "MANUAL", e.getMessage()));
            throw new RuntimeException("Failed to fetch historical data for " + ticker, e);
        }
    }

    @Transactional
    public void refreshIndices() {
        for (String ticker : SUPPORTED_ETFS) {
            try { fetchHistoricalData(ticker); } catch (Exception e) {}
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
        return passwordEncoder.matches(rawPassword, userRepository.findByUsername(userDetails.getUsername()).get().getPassword());
    }

    public List<MarketData> getTopGainers() { return marketDataRepository.findByType("GAINER"); }
    public List<MarketData> getTopLosers() { return marketDataRepository.findByType("LOSER"); }
    public List<MarketData> getMostActive() { return marketDataRepository.findByType("ACTIVE"); }
}