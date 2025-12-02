package com.treishvaam.financeapi.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.apistatus.ApiFetchStatus;
import com.treishvaam.financeapi.apistatus.ApiFetchStatusRepository;
import com.treishvaam.financeapi.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service("apiMarketDataService")
public class MarketDataService {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private static final int CACHE_DURATION_MINUTES = 30;
    
    private static final List<String> SUPPORTED_ETFS = Arrays.asList(
            "^GSPC", "^DJI", "^IXIC", "^RUT", "^NYA", "^VIX", "^GDAXI", "^FTSE", "^HSI",
            "GC=F", "CL=F", "SI=F", "^NSEI", "^BSESN"
    );

    private static final Map<String, List<String>> PEER_MAP = Map.of(
            "^GSPC", List.of("^DJI", "^IXIC", "^RUT"),
            "^DJI", List.of("^GSPC", "^IXIC", "^RUT"),
            "^IXIC", List.of("^GSPC", "^DJI", "^RUT"),
            "^RUT", List.of("^GSPC", "^DJI", "^IXIC"),
            "^NSEI", List.of("^BSESN"),
            "^BSESN", List.of("^NSEI"),
            "GC=F", List.of("SI=F", "CL=F"),
            "CL=F", List.of("GC=F", "SI=F"),
            "SI=F", List.of("GC=F", "CL=F")
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
    @Qualifier("apiMarketDataFactory")
    private MarketDataFactory marketDataFactory;
    
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
    
    @Value("${spring.datasource.url}")
    private String dbUrl;
    @Value("${spring.datasource.username}")
    private String dbUsername;
    @Value("${spring.datasource.password}")
    private String dbPassword;
    
    @Value("${app.python.script.path:scripts/market_data_updater.py}")
    private String pythonScriptPath;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initializeData() {
        logger.info("STARTUP: Initializing Market Data Service...");
        // CSV load attempted here but data will primarily come from Python script
        csvHistoryLoader.loadCsvIfEmpty();
        logger.info("Startup initialization complete.");
    }

    @Transactional
    public void runPythonHistoryAndQuoteUpdate(String triggerSource) {
        String apiLabel = "Market Data Pipeline (Python)";
        logger.info("Starting Python data update script... Trigger: {}", triggerSource);
        
        ApiFetchStatus status = new ApiFetchStatus(apiLabel, "PENDING", triggerSource, "Script starting...");
        apiFetchStatusRepository.save(status);
        
        try {
            // Using "python3" to ensure correct environment
            ProcessBuilder pb = new ProcessBuilder(
                "python3", 
                pythonScriptPath,
                dbUrl,
                dbUsername,
                dbPassword
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[Python] {}", line);
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Python script finished successfully.");
                status.setStatus("SUCCESS");
                status.setDetails("Python script finished successfully.");
            } else {
                logger.error("Python script failed with exit code: {}", exitCode);
                status.setStatus("FAILURE");
                status.setDetails("Python script failed with exit code: " + exitCode + ". Output: " + output.substring(0, Math.min(output.length(), 1000)));
            }
        } catch (Exception e) {
            logger.error("FATAL: Failed to run Python update script: {}", e.getMessage(), e);
            status.setStatus("FAILURE");
            status.setDetails("Java exception: " + e.getMessage());
        }
        apiFetchStatusRepository.save(status);
    }

    // --- Caching for the Global Ticker Bar ---
    @Cacheable(value = "quotesBatch", key = "#tickers.hashCode()", unless = "#result == null || #result.isEmpty()")
    public List<QuoteData> getQuotesBatch(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return Collections.emptyList();
        }
        return quoteDataRepository.findByTickerIn(tickers);
    }

    // --- Caching for the Market Widget (Chart + Data) ---
    @Cacheable(value = "marketWidget", key = "#ticker", unless = "#result == null")
    public WidgetDataDto getWidgetData(String ticker) {
        QuoteData quote = quoteDataRepository.findById(ticker).orElse(null);
        
        if (quote == null) {
                logger.warn("No quote data found in DB for {}.", ticker);
        }
        
        List<HistoricalPrice> history = historicalPriceRepository.findByTickerOrderByPriceDateAsc(ticker);

        List<String> peerTickers = PEER_MAP.getOrDefault(ticker, Collections.emptyList());
        List<QuoteData> peers = Collections.emptyList();
        if (!peerTickers.isEmpty()) {
            peers = quoteDataRepository.findByTickerIn(peerTickers);
        }

        return new WidgetDataDto(quote, history, peers);
    }

    // --- DISABLED METHODS ---
    private void bridgeDataGap() {}
    private void initializeHolidays() {}
    @Transactional
    public void updateAllQuotes(String triggerSource) {}

    // ================= MOVERS FETCHING (Standardized) =================

    @Transactional
    public void fetchAndStoreMarketData(String market, String triggerSource) {
        MarketDataProvider provider = marketDataFactory.getMoversProvider(market);
        
        // 1. Gainers
        try {
            List<MarketData> gainers = provider.fetchTopGainers();
            marketDataRepository.deleteByType("GAINER");
            if (gainers != null && !gainers.isEmpty()) marketDataRepository.saveAll(gainers);
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Movers - Top Gainers", "SUCCESS", triggerSource, "Fetched " + (gainers != null ? gainers.size() : 0) + " items."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Movers - Top Gainers", "FAILURE", triggerSource, e.getMessage()));
        }
        
        // 2. Losers
        try {
            List<MarketData> losers = provider.fetchTopLosers();
            marketDataRepository.deleteByType("LOSER");
            if (losers != null && !losers.isEmpty()) marketDataRepository.saveAll(losers);
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Movers - Top Losers", "SUCCESS", triggerSource, "Fetched " + (losers != null ? losers.size() : 0) + " items."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Movers - Top Losers", "FAILURE", triggerSource, e.getMessage()));
        }
        
        // 3. Active
        try {
            List<MarketData> active = provider.fetchMostActive();
            marketDataRepository.deleteByType("ACTIVE");
            if (active != null && !active.isEmpty()) marketDataRepository.saveAll(active);
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Movers - Most Active", "SUCCESS", triggerSource, "Fetched " + (active != null ? active.size() : 0) + " items."));
        } catch (Exception e) {
            apiFetchStatusRepository.save(new ApiFetchStatus("Market Movers - Most Active", "FAILURE", triggerSource, e.getMessage()));
        }
    }
    
    // --- UPDATED: REFRESH INDICES NOW TRIGGERS PYTHON ---
    @Transactional
    public void refreshIndices() {
        logger.info("Manual refresh of Indices triggered. Executing Python pipeline.");
        runPythonHistoryAndQuoteUpdate("MANUAL_TRIGGER");
    }

    // --- Legacy / Fallback for specific tickers ---
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
            apiFetchStatusRepository.save(new ApiFetchStatus("Legacy Chart (" + ticker + ")", "FAILURE", "MANUAL", e.getMessage()));
            throw new RuntimeException("Failed to fetch historical data for " + ticker, e);
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