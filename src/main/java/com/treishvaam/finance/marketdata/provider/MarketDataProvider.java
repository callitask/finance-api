package com.treishvaam.finance.marketdata.provider;

import com.treishvaam.finance.marketdata.entity.MarketData;
import java.util.List;

public interface MarketDataProvider {
    List<MarketData> fetchTopGainers();
    List<MarketData> fetchTopLosers();
    List<MarketData> fetchMostActive();
}
