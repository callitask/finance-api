package com.treishvaam.finance.marketdata.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class MarketDataFactory {

    @Autowired
    @Qualifier("fmpProvider")
    private MarketDataProvider fmpProvider;

    @Autowired
    @Qualifier("breezeProvider")
    private MarketDataProvider breezeProvider;

    public MarketDataProvider getProvider(String market) {
        // As per the roadmap, FMP is primary for US, Breeze is primary for India.
        if ("IN".equalsIgnoreCase(market)) {
            return breezeProvider;
        }
        // Default to FMP for US and other global markets for now.
        return fmpProvider;
    }
}