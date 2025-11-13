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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections; // --- NEW IMPORT ---
import java.util.List;
import java.util.Map; // --- NEW IMPORT ---
import java.util.Optional;
import java.util.stream.Collectors;

@Service("apiMarketDataService")
public class MarketDataService {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private static final int CACHE_DURATION_MINUTES = 30;
    // This list contains the real tickers
    private static final List<String> SUPPORTED_ETFS = Arrays.asList(
            "^GSPC", "^DJI", "^IXIC", "^RUT", "^NYA", "^VIX", "^GDAXI", "^FTSE", "^HSI",
            "GC=F", "CL=F", "SI=F", "^NSEI", "^BSESN"
    );

    // --- NEW: Peer map for comparison carousel ---
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
    private MarketHolidayRepository marketHolidayRepository;
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
        
        // This will now run and see the empty table (after you TRUNCATE)
        // and do nothing, which is correct.
        csvHistoryLoader.loadCsvIfEmpty();
        
        logger.info("Startup initialization complete. Python script will be triggered by initializer/scheduler.");
    }
    @Transactional
    public void runPythonHistoryAndQuoteUpdate(String triggerSource) {
        logger.info("Starting Python data update script... Trigger: {}", triggerSource);
        ApiFetchStatus status = new ApiFetchStatus("Python Data Update", "PENDING", triggerSource, "Script starting...");
        apiFetchStatusRepository.save(status);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "python", // Changed to "python" for Windows compatibility
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
                // --- FIX 1 ---
                status.setDetails("Python script finished successfully.");
            } else {
                logger.error("Python script failed with exit code: {}", exitCode);
                status.setStatus("FAILURE");
                // --- FIX 2 --- (Increased substring to 1000 for more detail)
                status.setDetails("Python script failed with exit code: " + exitCode + ". Output: " + output.substring(0, Math.min(output.length(), 1000)));
            }
        } catch (Exception e) {
            logger.error("FATAL: Failed to run Python update script: {}", e.getMessage(), e);
            status.setStatus("FAILURE");
            // --- FIX 3 ---
            status.setDetails("Java exception: " + e.getMessage());
        }
        
        apiFetchStatusRepository.save(status);
        logger.info("Python data update complete. Trigger: {}", triggerSource);
    }
    // --- DISABLED METHOD ---
    private void bridgeDataGap() {
        logger.warn("bridgeDataGap() is TEMPORARILY DISABLED. Python script handles this now.");
    }
    // --- DISABLED METHOD ---
    private void initializeHolidays() {
        logger.warn("initializeHolidays() is TEMPORARILY DISABLED. Python script handles this now.");
    }

    // --- NEW METHOD: For Global Market Ticker ---
    public List<QuoteData> getQuotesBatch(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return Collections.emptyList();
        }
        return quoteDataRepository.findByTickerIn(tickers);
    }

    public WidgetDataDto getWidgetData(String ticker) {
        // This logic is now correct. It will find data for "^GSPC", etc.
        QuoteData quote = quoteDataRepository.findById(ticker).orElse(null);
        
        if (quote == null) {
                logger.warn("No quote data found in DB for {}. It may not have been fetched by the Python script yet.", ticker);
        }
        
        List<HistoricalPrice> history = historicalPriceRepository.findByTickerOrderByPriceDateAsc(ticker);

        // --- NEW: Fetch peers ---
        List<String> peerTickers = PEER_MAP.getOrDefault(ticker, Collections.emptyList());
        List<QuoteData> peers = Collections.emptyList();
        if (!peerTickers.isEmpty()) {
            peers = quoteDataRepository.findByTickerIn(peerTickers);
        }

        return new WidgetDataDto(quote, history, peers); // --- MODIFIED ---
    }
    // --- DISABLED METHOD ---
    @Transactional
    public void updateAllQuotes(String triggerSource) {
        logger.warn("updateAllQuotes() is TEMPORARILY DISABLED. Python script handles this now.");
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
        // This admin function will now use the correct tickers
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