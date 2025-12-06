package com.treishvaam.financeapi.newshighlight;

import com.google.gson.Gson;
import com.treishvaam.financeapi.apistatus.ApiFetchStatus;
import com.treishvaam.financeapi.apistatus.ApiFetchStatusRepository;
import com.treishvaam.financeapi.common.SystemProperty;
import com.treishvaam.financeapi.common.SystemPropertyRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker; // IMPORTED
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class NewsHighlightService {
  private final NewsHighlightRepository newsHighlightRepository;
  private final SystemPropertyRepository systemPropertyRepository;
  private final RestTemplate restTemplate = new RestTemplate();
  private final Gson gson = new Gson();
  private static final DateTimeFormatter NEWS_API_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final String LAST_FETCH_KEY = "news_last_fetch_timestamp";

  @Autowired private ApiFetchStatusRepository apiFetchStatusRepository;

  @Value("${newsdata.api.key}")
  private String apiKey;

  @Autowired
  public NewsHighlightService(
      NewsHighlightRepository newsHighlightRepository,
      SystemPropertyRepository systemPropertyRepository) {
    this.newsHighlightRepository = newsHighlightRepository;
    this.systemPropertyRepository = systemPropertyRepository;
  }

  public List<NewsHighlight> getNewsHighlights() {
    fetchNewsIfStale();
    return newsHighlightRepository.findTop10ByOrderByPublishedAtDesc();
  }

  public List<NewsHighlight> getArchivedNews() {
    fetchNewsIfStale();
    Pageable pageable = PageRequest.of(1, 10, Sort.by("publishedAt").descending());
    return newsHighlightRepository.findAllByOrderByPublishedAtDesc(pageable);
  }

  private void fetchNewsIfStale() {
    Optional<SystemProperty> lastFetchProp = systemPropertyRepository.findById(LAST_FETCH_KEY);
    LocalDateTime lastFetchTime =
        lastFetchProp.map(prop -> prop.getPropValue()).orElse(LocalDateTime.MIN);
    long hoursSinceLastFetch = ChronoUnit.HOURS.between(lastFetchTime, LocalDateTime.now());

    if (hoursSinceLastFetch >= 3) {
      fetchAndSaveNewHighlights("AUTOMATIC");
    }
  }

  @Transactional
  // --- NEW: Circuit Breaker ---
  @CircuitBreaker(name = "newsApi", fallbackMethod = "fallbackNewsFetch")
  public void fetchAndSaveNewHighlights(String triggerSource) {
    String url =
        "https://newsdata.io/api/1/news?apikey="
            + apiKey
            + "&language=en&category=business,technology";
    try {
      String jsonResponse = restTemplate.getForObject(url, String.class);
      NewsApiResponseDto response = gson.fromJson(jsonResponse, NewsApiResponseDto.class);

      if (response != null
          && "success".equals(response.getStatus())
          && response.getResults() != null) {
        List<NewsHighlight> newHighlights =
            response.getResults().stream()
                .map(this::convertToEntity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (NewsHighlight highlight : newHighlights) {
          if (!newsHighlightRepository.existsByTitle(highlight.getTitle())) {
            try {
              newsHighlightRepository.save(highlight);
            } catch (Exception e) {
              // Ignore duplicates
            }
          }
        }
        apiFetchStatusRepository.save(
            new ApiFetchStatus(
                "News Highlights", "SUCCESS", triggerSource, "Data fetched successfully."));
      } else {
        String errorDetails =
            response != null
                ? "API returned status: " + response.getStatus()
                : "Empty response from API.";
        apiFetchStatusRepository.save(
            new ApiFetchStatus("News Highlights", "FAILURE", triggerSource, errorDetails));
        throw new RuntimeException("API Error: " + errorDetails); // Trigger CB
      }
    } catch (Exception e) {
      System.err.println("Error fetching news from Newsdata.io: " + e.getMessage());
      apiFetchStatusRepository.save(
          new ApiFetchStatus("News Highlights", "FAILURE", triggerSource, e.getMessage()));
      throw new RuntimeException(e); // Trigger CB
    } finally {
      systemPropertyRepository.save(new SystemProperty(LAST_FETCH_KEY, LocalDateTime.now()));
    }
  }

  // --- NEW: Fallback Method ---
  public void fallbackNewsFetch(String triggerSource, Throwable t) {
    System.err.println("Circuit Breaker Open for News API: " + t.getMessage());
    apiFetchStatusRepository.save(
        new ApiFetchStatus("News Highlights", "SKIPPED", triggerSource, "Circuit Breaker Open"));
  }

  private NewsHighlight convertToEntity(NewsArticleDto dto) {
    if (dto.getPubDate() == null || dto.getLink() == null || dto.getTitle() == null) {
      return null;
    }
    try {
      LocalDateTime publishedAt = LocalDateTime.parse(dto.getPubDate(), NEWS_API_FORMATTER);
      return new NewsHighlight(dto.getTitle(), dto.getLink(), dto.getSource_id(), publishedAt);
    } catch (java.time.format.DateTimeParseException e) {
      return null;
    }
  }

  @Transactional
  public String deduplicateNewsArticles() {
    List<String> uniqueTitles = newsHighlightRepository.findDistinctTitles();
    int duplicatesRemoved = 0;
    for (String title : uniqueTitles) {
      List<NewsHighlight> articles =
          newsHighlightRepository.findByTitleOrderByPublishedAtDesc(title);
      if (articles.size() > 1) {
        List<NewsHighlight> articlesToRemove = articles.subList(1, articles.size());
        newsHighlightRepository.deleteAll(articlesToRemove);
        duplicatesRemoved += articlesToRemove.size();
      }
    }
    return "De-duplication complete. Removed " + duplicatesRemoved + " duplicate articles.";
  }
}
