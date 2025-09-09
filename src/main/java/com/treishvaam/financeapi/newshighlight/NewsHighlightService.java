package com.treishvaam.financeapi.newshighlight;

import com.google.gson.Gson;
import com.treishvaam.financeapi.common.SystemProperty;
import com.treishvaam.financeapi.common.SystemPropertyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NewsHighlightService {

    private final NewsHighlightRepository newsHighlightRepository;
    private final SystemPropertyRepository systemPropertyRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();
    private static final DateTimeFormatter NEWS_API_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LAST_FETCH_KEY = "news_last_fetch_timestamp";

    @Value("${newsdata.api.key}")
    private String apiKey;

    @Autowired
    public NewsHighlightService(NewsHighlightRepository newsHighlightRepository, SystemPropertyRepository systemPropertyRepository) {
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
        LocalDateTime lastFetchTime = lastFetchProp.map(SystemProperty::getPropValue).orElse(LocalDateTime.MIN);
        long hoursSinceLastFetch = ChronoUnit.HOURS.between(lastFetchTime, LocalDateTime.now());

        if (hoursSinceLastFetch >= 3) {
            fetchAndSaveNewHighlights();
        }
    }

    @Transactional
    public void fetchAndSaveNewHighlights() {
        String url = "https://newsdata.io/api/1/news?apikey=" + apiKey + "&language=en&category=business,technology";
        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);
            NewsApiResponseDto response = gson.fromJson(jsonResponse, NewsApiResponseDto.class);

            if (response != null && "success".equals(response.getStatus()) && response.getResults() != null) {
                List<NewsHighlight> newHighlights = response.getResults().stream()
                    .map(this::convertToEntity)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                for (NewsHighlight highlight : newHighlights) {
                    if (!newsHighlightRepository.existsByTitle(highlight.getTitle())) {
                        try {
                            newsHighlightRepository.save(highlight);
                        } catch (Exception e) {
                            // Ignore secondary exception on unique link constraint
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching news from Newsdata.io: " + e.getMessage());
        } finally {
            systemPropertyRepository.save(new SystemProperty(LAST_FETCH_KEY, LocalDateTime.now()));
        }
    }

    private NewsHighlight convertToEntity(NewsArticleDto dto) {
        if (dto.getPubDate() == null || dto.getLink() == null || dto.getTitle() == null) {
            return null;
        }
        try {
            LocalDateTime publishedAt = LocalDateTime.parse(dto.getPubDate(), NEWS_API_FORMATTER);
            return new NewsHighlight(dto.getTitle(), dto.getLink(), dto.getSource_id(), publishedAt);
        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("Could not parse date from API: " + dto.getPubDate() + ". Skipping article.");
            return null;
        }
    }

    @Transactional
    public String deduplicateNewsArticles() {
        // FIX: This logic now de-duplicates based on TITLE
        List<String> uniqueTitles = newsHighlightRepository.findDistinctTitles();
        int duplicatesRemoved = 0;

        for (String title : uniqueTitles) {
            List<NewsHighlight> articles = newsHighlightRepository.findByTitleOrderByPublishedAtDesc(title);
            if (articles.size() > 1) {
                // Keep the first one (the newest) and delete the rest
                List<NewsHighlight> articlesToRemove = articles.subList(1, articles.size());
                newsHighlightRepository.deleteAll(articlesToRemove);
                duplicatesRemoved += articlesToRemove.size();
            }
        }
        return "De-duplication complete. Removed " + duplicatesRemoved + " duplicate articles.";
    }
}