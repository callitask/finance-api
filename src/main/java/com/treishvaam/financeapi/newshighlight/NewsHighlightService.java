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
import net.coobird.thumbnailator.Thumbnails; // Use Thumbnailator
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

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (repository.count() == 0) {
      logger.info("📰 News DB Empty. Triggering initial fetch...");
      fetchAndStoreNews();
    }
  }

  public Page<NewsHighlight> getHighlights(Pageable pageable) {
    return repository.findByIsArchivedFalseOrderByPublishedAtDesc(pageable);
  }

  @Scheduled(fixedRate = 900000)
  public void fetchAndStoreNews() {
    logger.info("🌍 Starting Global News Intelligence Cycle...");
    try {
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
        if (!isAllowedSource(article.getSourceId())) continue;
        if (saveArticleSafe(article)) newCount++;
      }
      logger.info("✅ Cycle Complete. Fetched: {}, Saved: {}", articles.size(), newCount);
      archiveOldStories();
    } catch (Exception e) {
      logger.error("❌ News Fetch Failed: {}", e.getMessage());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean saveArticleSafe(NewsDataArticle apiArticle) {
    try {
      if (repository.existsByLink(apiArticle.getLink())) return false;

      NewsHighlight news = new NewsHighlight();
      news.setTitle(apiArticle.getTitle());
      news.setLink(apiArticle.getLink());
      news.setSource(formatSourceName(apiArticle.getSourceId()));
      news.setDescription(apiArticle.getDescription());

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

      // --- IMAGE INTELLIGENCE ---
      String bestImageUrl = resolveBestImage(apiArticle);
      news.setImageUrl(bestImageUrl);
      news.setArchived(false);

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
    if (candidateUrl == null || candidateUrl.isEmpty()) {
      candidateUrl = scrapeImageFromUrl(article.getLink());
    }

    if (candidateUrl != null && !candidateUrl.isEmpty()) {
      // Try to download and cache locally
      String localFilename = downloadAndOptimizeImage(candidateUrl);
      if (localFilename != null) return localFilename; // Returns "/api/uploads/..."

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
              .timeout(5000)
              .get();
      Element metaOg = doc.selectFirst("meta[property='og:image']");
      if (metaOg != null) return metaOg.attr("content");
      Element metaTwitter = doc.selectFirst("meta[name='twitter:image']");
      if (metaTwitter != null) return metaTwitter.attr("content");
    } catch (Exception e) {
      /* Ignore */
    }
    return null;
  }

  private String downloadAndOptimizeImage(String imageUrl) {
    try {
      URL url = new URL(imageUrl);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);

      try (InputStream in = conn.getInputStream()) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        // --- OPTIMIZATION ---
        Thumbnails.of(in)
            .size(800, 600)
            .outputFormat("webp")
            .outputQuality(0.85)
            .toOutputStream(os);

        String filename = "news-" + UUID.randomUUID() + ".webp";
        // Returns "/api/uploads/news-....webp"
        return fileStorageService.storeFile(
            new ByteArrayInputStream(os.toByteArray()), filename, "image/webp");
      }
    } catch (Exception e) {
      logger.warn("⚠️ Image download failed for {}: {}", imageUrl, e.getMessage());
      return null;
    }
  }

  private boolean isAllowedSource(String sourceId) {
    if (sourceId == null) return false;
    String lower = sourceId.toLowerCase();
    for (String allowed : ALLOWED_SOURCES) if (lower.contains(allowed)) return true;
    return false;
  }

  private String formatSourceName(String sourceId) {
    if (sourceId == null) return "Global News";
    return Arrays.stream(sourceId.split(" "))
        .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }

  private void archiveOldStories() {
    List<NewsHighlight> activeNews = repository.findByIsArchivedFalseOrderByPublishedAtDesc();
    if (activeNews.size() > 50) {
      List<NewsHighlight> toArchive = activeNews.subList(50, activeNews.size());
      for (NewsHighlight news : toArchive) news.setArchived(true);
      repository.saveAll(toArchive);
      logger.info("🗄️ Auto-Archived {} old stories.", toArchive.size());
    }
  }
}
