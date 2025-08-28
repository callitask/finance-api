package com.treishvaam.financeapi.marketdata;

import java.util.List;

public interface MarketDataProvider {
    List<MarketData> fetchTopGainers();
    List<MarketData> fetchTopLosers();
    List<MarketData> fetchMostActive();
}
