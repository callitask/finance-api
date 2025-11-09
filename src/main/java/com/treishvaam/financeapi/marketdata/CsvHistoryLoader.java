package com.treishvaam.financeapi.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CsvHistoryLoader {

    private static final Logger logger = LoggerFactory.getLogger(CsvHistoryLoader.class);
    private static final String CSV_PATH = "marketdata/30_yr_stock_market_data.csv";

    @Autowired
    private HistoricalPriceRepository historicalPriceRepository;

    // MAPPING: CSV Header Name -> Our Database Ticker (Standard US ETFs for reliable API access)
    private static final Map<String, String> HEADER_TO_TICKER_MAP = Map.of(
            "Dow Jones (^DJI)", "DIA",
            "Nasdaq (^IXIC)", "QQQ",
            "S&P500 (^GSPC)", "SPY",
            "Russell 2000 (^RUT)", "IWM",
            "NYSE Composite (^NYA)", "VTI",
            "CBOE Volitility (^VIX)", "VIXY", // Matched typo in CSV header if present
            "DAX Index (^GDAXI)", "EWG",
            "FTSE 100 (^FTSE)", "EWU",
            "Hang Seng Index (^HSI)", "EWH"
    );

    @Transactional
    public void loadCsvIfEmpty() {
        if (historicalPriceRepository.count() > 0) {
            logger.info("Historical data already exists. Skipping CSV load.");
            return;
        }

        logger.info("Historical data table is empty. Starting cold-load from CSV: {}", CSV_PATH);
        long startTime = System.currentTimeMillis();

        try {
            ClassPathResource resource = new ClassPathResource(CSV_PATH);
            if (!resource.exists()) {
                logger.error("CSV file NOT FOUND at {}. Please ensure file is in src/main/resources/marketdata/", CSV_PATH);
                return;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                String headerLine = br.readLine();
                if (headerLine == null) return;

                // Handle potential BOM or weird start characters common in some CSVs
                if (headerLine.startsWith("\uFEFF")) {
                    headerLine = headerLine.substring(1);
                }

                String[] headers = headerLine.split(",");
                Map<Integer, String> colIndexToTicker = new HashMap<>();

                // FIXED LOOP: Replaced lambda with standard for-loop to avoid compilation error
                for (int i = 0; i < headers.length; i++) {
                    String cleanHeader = headers[i].trim();
                    for (Map.Entry<String, String> entry : HEADER_TO_TICKER_MAP.entrySet()) {
                        // Fuzzy match to handle potential minor CSV header variations
                        if (cleanHeader.equalsIgnoreCase(entry.getKey()) || cleanHeader.contains(entry.getKey())) {
                            colIndexToTicker.put(i, entry.getValue());
                            break;
                        }
                    }
                }

                if (colIndexToTicker.isEmpty()) {
                    logger.error("No matching headers found in CSV. Checked headers: {}", (Object) headers);
                    return;
                }
                logger.info("Found {} matching columns to import: {}", colIndexToTicker.size(), colIndexToTicker.values());

                List<HistoricalPrice> batch = new ArrayList<>();
                String line;
                int totalRecords = 0;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    // Robust split that handles potential empty values between commas better
                    String[] values = line.split(",", -1); 

                    try {
                        // Ensure we have a date and standard price columns
                         if (values.length > 0 && !values[0].trim().isEmpty()) {
                             LocalDate date = LocalDate.parse(values[0].trim(), dtf);

                             for (Map.Entry<Integer, String> mapping : colIndexToTicker.entrySet()) {
                                 int colIdx = mapping.getKey();
                                 String ticker = mapping.getValue();

                                 if (colIdx < values.length) {
                                     String priceStr = values[colIdx].trim();
                                     // Basic validation before parsing BigDecimal
                                     if (!priceStr.isEmpty() && !priceStr.equalsIgnoreCase("null") && !priceStr.equals(".")) {
                                         try {
                                             HistoricalPrice hp = new HistoricalPrice();
                                             hp.setTicker(ticker);
                                             hp.setPriceDate(date);
                                             hp.setClosePrice(new BigDecimal(priceStr));
                                             batch.add(hp);
                                         } catch (NumberFormatException nfe) {
                                             // Ignore individual bad number cells
                                         }
                                     }
                                 }
                             }
                         }
                    } catch (Exception e) {
                        logger.debug("Skipping row due to parse error: {}", e.getMessage());
                    }

                    if (batch.size() >= 5000) { // Increased batch size for speed
                        historicalPriceRepository.saveAll(batch);
                        totalRecords += batch.size();
                        batch.clear();
                        logger.info("Loaded {} records...", totalRecords);
                    }
                }

                if (!batch.isEmpty()) {
                    historicalPriceRepository.saveAll(batch);
                    totalRecords += batch.size();
                }

                logger.info("SUCCESS: Cold-loaded {} historical records in {} ms.", totalRecords, System.currentTimeMillis() - startTime);
            }
        } catch (Exception e) {
            logger.error("FATAL: CSV load failed: {}", e.getMessage(), e);
        }
    }
}