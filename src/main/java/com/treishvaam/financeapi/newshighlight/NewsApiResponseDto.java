package com.treishvaam.financeapi.newshighlight;

import java.util.List;

public class NewsApiResponseDto {
    private String status;
    private int totalResults;
    private List<NewsArticleDto> results;

    public NewsApiResponseDto() {}

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(int totalResults) {
        this.totalResults = totalResults;
    }

    public List<NewsArticleDto> getResults() {
        return results;
    }

    public void setResults(List<NewsArticleDto> results) {
        this.results = results;
    }
}