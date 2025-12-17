package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.newshighlight.dto.NewsDataArticle;
import com.treishvaam.financeapi.newshighlight.dto.NewsDataResponse;
import com.treishvaam.financeapi.service.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.coobird.thumbnailator.Thumbnails;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class NewsHighlightService {

  private static final Logger logger = LoggerFactory.getLogger(NewsHighlightService.class);

  private final NewsHighlightRepository repository;
  private final FileStorageService fileStorageService;
  private final RestTemplate restTemplate;

  @Value("${newsdata.api.key}")
  private String apiKey;

  // ALLOWLIST: Tier-1 Global Business News Sources Only
  private static final List<String> ALLOWED_SOURCES =
      Arrays.asList(
          "bloomberg",
          "reuters",
          "cnbc",
          "wsj",
          "financial times",
          "economist",
          "marketwatch",
          "investopedia",
          "business insider",
          "forbes",
          "bbc business",
          "cnn business",
          "yahoo finance",
          "techcrunch",
          "moneycontrol",
          "livemint",
          "economic times",
          "business standard");

  public NewsHighlightService(
      NewsHighlightRepository repository, FileStorageService fileStorageService) {
    this.repository = repository;
    this.fileStorageService = fileStorageService;
    this.restTemplate = new RestTemplate();
  }

  /** Run on Startup to ensure we have data immediately. */
  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (repository.count() == 0) {
      logger.info("📰 News DB Empty. Triggering initial fetch...");
      fetchAndStoreNews();
    }
  }

  /** Public accessor for Controller */
  public Page<NewsHighlight> getHighlights(Pageable pageable) {
    return repository.findByIsArchivedFalseOrderByPublishedAtDesc(pageable);
  }

  /** Schedule: Every 15 minutes (900,000 ms). 96 requests/day. Safe within 200/day limit. */
  @Scheduled(fixedRate = 900000)
  public void fetchAndStoreNews() {
    logger.info("🌍 Starting Global News Intelligence Cycle...");

    try {
      // 1. Fetch from NewsData.io (Business Category, English)
      String url =
          "https://newsdata.io/api/1/news?apikey=" + apiKey + "&category=business&language=en";
      ResponseEntity<NewsDataResponse> response =
          restTemplate.getForEntity(url, NewsDataResponse.class);

      if (response.getBody() == null || response.getBody().getResults() == null) {
        logger.warn("⚠️ News API returned empty response.");
        return;
      }

      List<NewsDataArticle> articles = response.getBody().getResults();
      int newCount = 0;

      for (NewsDataArticle article : articles) {
        // 2. Filter: Must be in AllowList or have high credibility
        if (!isAllowedSource(article.getSourceId())) {
          continue;
        }

        // 3. Process & Save (Isolated Transaction)
        boolean saved = saveArticleSafe(article);
        if (saved) newCount++;
      }

      logger.info("✅ Cycle Complete. Fetched: {}, Saved: {}", articles.size(), newCount);

      // 4. Maintenance: Keep only top 50 active
      archiveOldStories();

    } catch (Exception e) {
      logger.error("❌ News Fetch Failed: {}", e.getMessage());
    }
  }

  /** Isolated Transaction to handle duplicates without rolling back the whole batch. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean saveArticleSafe(NewsDataArticle apiArticle) {
    try {
      if (repository.existsByLink(apiArticle.getLink())) {
        return false; // Skip duplicate
      }

      NewsHighlight news = new NewsHighlight();
      news.setTitle(apiArticle.getTitle());
      news.setLink(apiArticle.getLink());
      news.setSource(formatSourceName(apiArticle.getSourceId()));
      news.setDescription(apiArticle.getDescription());

      // Handle Date
      if (apiArticle.getPubDate() != null) {
        try {
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
          news.setPublishedAt(LocalDateTime.parse(apiArticle.getPubDate(), formatter));
        } catch (Exception e) {
          news.setPublishedAt(LocalDateTime.now());
        }
      } else {
        news.setPublishedAt(LocalDateTime.now());
      }

      // 5. Image Intelligence (Scrape -> Download -> Optimize)
      String bestImageUrl = resolveBestImage(apiArticle);
      news.setImageUrl(bestImageUrl);

      news.setArchived(false); // Active by default

      repository.save(news);
      return true;

    } catch (DataIntegrityViolationException e) {
      return false;
    } catch (Exception e) {
      logger.error("⚠️ Error saving article '{}': {}", apiArticle.getTitle(), e.getMessage());
      return false;
    }
  }

  private String resolveBestImage(NewsDataArticle article) {
    String candidateUrl = article.getImageUrl();

    // Step A: If API has no image, try scraping the article itself
    if (candidateUrl == null || candidateUrl.isEmpty()) {
      candidateUrl = scrapeImageFromUrl(article.getLink());
    }

    // Step B: If we found a URL, try to download/optimize it
    if (candidateUrl != null && !candidateUrl.isEmpty()) {
      String localFilename = downloadAndOptimizeImage(candidateUrl);
      if (localFilename != null) {
        return localFilename; // Return "/api/uploads/news-xyz.webp"
      }
      return candidateUrl; // Fallback to remote URL
    }

    return null;
  }

  private String scrapeImageFromUrl(String articleUrl) {
    try {
      Document doc =
          Jsoup.connect(articleUrl)
              .userAgent(
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .timeout(5000) // Increased timeout
              .get();

      // Try OpenGraph image
      Element metaOgImage = doc.selectFirst("meta[property='og:image']");
      if (metaOgImage != null) return metaOgImage.attr("content");

      // Try Twitter image
      Element metaTwitterImage = doc.selectFirst("meta[name='twitter:image']");
      if (metaTwitterImage != null) return metaTwitterImage.attr("content");

    } catch (Exception e) {
      // Scraping failed, ignore
    }
    return null;
  }

  private String downloadAndOptimizeImage(String imageUrl) {
    try {
      // Spoof User-Agent to avoid 403 Forbidden
      URL url = new URL(imageUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      try (InputStream in = connection.getInputStream()) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // --- ENTERPRISE OPTIMIZATION ---
        // Use Thumbnailator for efficient WebP conversion
        Thumbnails.of(in)
            .size(800, 600) // Standardize News Card dimensions
            .outputFormat("webp")
            .outputQuality(0.85) // High quality, low size
            .toOutputStream(os);

        String filename = "news-" + UUID.randomUUID() + ".webp";

        // This will now return "/api/uploads/news-....webp"
        return fileStorageService.storeFile(
            new ByteArrayInputStream(os.toByteArray()), filename, "image/webp");
      }
    } catch (Exception e) {
      // If optimization fails (e.g. unknown format), fallback to original remote URL
      logger.warn("Failed to optimize image: {}. Error: {}", imageUrl, e.getMessage());
      return null;
    }
  }

  private boolean isAllowedSource(String sourceId) {
    if (sourceId == null) return false;
    String lower = sourceId.toLowerCase();
    for (String allowed : ALLOWED_SOURCES) {
      if (lower.contains(allowed)) return true;
    }
    return false;
  }

  private String formatSourceName(String sourceId) {
    if (sourceId == null) return "Global News";
    return Arrays.stream(sourceId.split(" "))
        .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }

  private void archiveOldStories() {
    List<NewsHighlight> activeNews = repository.findByIsArchivedFalseOrderByPublishedAtDesc();
    if (activeNews.size() > 50) {
      List<NewsHighlight> toArchive = activeNews.subList(50, activeNews.size());
      for (NewsHighlight news : toArchive) {
        news.setArchived(true);
      }
      repository.saveAll(toArchive);
      logger.info("🗄️ Auto-Archived {} old stories.", toArchive.size());
    }
  }
}
