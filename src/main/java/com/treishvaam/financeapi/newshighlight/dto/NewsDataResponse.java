package com.treishvaam.financeapi.newshighlight.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsDataResponse {
  private String status;
  private int totalResults;
  private List<NewsDataArticle> results;
  private String nextPage;

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

  public List<NewsDataArticle> getResults() {
    return results;
  }

  public void setResults(List<NewsDataArticle> results) {
    this.results = results;
  }

  public String getNextPage() {
    return nextPage;
  }

  public void setNextPage(String nextPage) {
    this.nextPage = nextPage;
  }
}
