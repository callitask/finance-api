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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    } else {
      // Run a gentle heal on startup
      healLegacyImages();
    }
  }

  public Page<NewsHighlight> getHighlights(Pageable pageable) {
    return repository.findByIsArchivedFalseOrderByPublishedAtDesc(pageable);
  }

  // --- Image Healer (Runs every 15 mins) ---
  @Scheduled(fixedRate = 900000)
  public void healLegacyImages() {
    logger.debug("🚑 Starting News Image Healer (Routine Check)...");

    // Safety Limit: Only check the most recent 50 articles
    Pageable limit = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "publishedAt"));
    List<NewsHighlight> recentNews =
        repository.findByIsArchivedFalseOrderByPublishedAtDesc(limit).getContent();

    int fixedCount = 0;
    for (NewsHighlight news : recentNews) {
      boolean needsRepair = false;
      String currentUrl = news.getImageUrl();

      // 1. Check for null or empty
      if (currentUrl == null || currentUrl.isEmpty()) {
        needsRepair = true;
      }
      // 2. Check for local paths that might be corrupt (0 bytes or missing)
      else if (currentUrl.startsWith("/api/uploads/") || currentUrl.startsWith("/uploads/")) {
        long size = fileStorageService.getFileSize(currentUrl);
        if (size <= 0) {
          needsRepair = true;
        }
      }

      if (needsRepair) {
        repairSingleNewsItem(news);
        fixedCount++;
      }
    }
    if (fixedCount > 0) logger.info("✅ Healer processed {} items.", fixedCount);
  }

  private void repairSingleNewsItem(NewsHighlight news) {
    try {
      String scrapedImageUrl = scrapeImageFromUrl(news.getLink());
      if (scrapedImageUrl != null && !scrapedImageUrl.isEmpty()) {
        String newLocalPath = downloadAndOptimizeImage(scrapedImageUrl);
        String finalUrl = (newLocalPath != null) ? newLocalPath : scrapedImageUrl;
        news.setImageUrl(finalUrl);
        repository.save(news);
      }
    } catch (Exception e) {
      // Silent fail is fine here
    }
  }

  // --- Main Fetch Cycle: Every 15 Minutes ---
  @Scheduled(fixedRate = 900000)
  public void fetchAndStoreNews() {
    logger.info("🌍 Starting News Fetch Cycle...");
    try {
      // API call to NewsData.io
      String url =
          "https://newsdata.io/api/1/news?apikey=" + apiKey + "&category=business&language=en";
      ResponseEntity<NewsDataResponse> response =
          restTemplate.getForEntity(url, NewsDataResponse.class);

      if (response.getBody() == null || response.getBody().getResults() == null) return;

      List<NewsDataArticle> articles = response.getBody().getResults();
      int newCount = 0;
      int duplicateCount = 0;

      for (NewsDataArticle article : articles) {
        if (!isAllowedSource(article.getSourceId())) continue;

        // Attempt to save
        boolean saved = saveArticleSafe(article);
        if (saved) {
          newCount++;
        } else {
          duplicateCount++;
        }
      }
      logger.info(
          "✅ Cycle Complete. Fetched: {}, New: {}, Skipped/Dup: {}",
          articles.size(),
          newCount,
          duplicateCount);

      archiveOldStories();
    } catch (Exception e) {
      logger.error("❌ News Fetch Cycle Error: {}", e.getMessage());
    }
  }

  /**
   * Safe save method that handles duplicates gracefully without exploding logs. Uses a new
   * transaction to ensure isolation.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean saveArticleSafe(NewsDataArticle apiArticle) {
    try {
      String rawLink = apiArticle.getLink();
      if (rawLink == null) return false;

      // Normalize link
      String normalizedLink = rawLink.trim();

      // 1. Check if Link exists (Primary Constraint)
      if (repository.existsByLink(normalizedLink)) {
        return false;
      }

      // 2. Check if Title exists (Secondary Duplicate Check)
      // This helps avoid same story from same source with slightly different URL params
      if (apiArticle.getTitle() != null && repository.existsByTitle(apiArticle.getTitle())) {
        return false;
      }

      NewsHighlight news = new NewsHighlight();
      news.setTitle(apiArticle.getTitle());
      news.setLink(normalizedLink); // Save the normalized link
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

      String bestImageUrl = resolveBestImage(apiArticle);
      news.setImageUrl(bestImageUrl);
      news.setArchived(false);

      repository.save(news);
      return true;

    } catch (DataIntegrityViolationException e) {
      // This catches the database constraint violation if the race condition occurs
      // We return false to indicate it wasn't saved, but we DO NOT log an error.
      // This silences the "Duplicate entry" log noise in the application logs.
      return false;
    } catch (Exception e) {
      logger.error("⚠️ Failed to save article: {}", e.getMessage());
      return false;
    }
  }

  private String resolveBestImage(NewsDataArticle article) {
    String candidateUrl = article.getImageUrl();
    if (candidateUrl == null || candidateUrl.isEmpty()) {
      candidateUrl = scrapeImageFromUrl(article.getLink());
    }

    if (candidateUrl != null && !candidateUrl.isEmpty()) {
      String localFilename = downloadAndOptimizeImage(candidateUrl);
      return (localFilename != null) ? localFilename : candidateUrl;
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
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, nRead);
        }
        buffer.flush();
        byte[] imageBytes = buffer.toByteArray();

        if (imageBytes.length == 0) return null;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String extension = "webp";
        String contentType = "image/webp";

        // Try WebP first
        try {
          Thumbnails.of(new ByteArrayInputStream(imageBytes))
              .size(800, 600)
              .outputFormat("webp")
              .outputQuality(0.85)
              .toOutputStream(os);
        } catch (Exception e) {
          os.reset();
          extension = "jpg";
          contentType = "image/jpeg";
          Thumbnails.of(new ByteArrayInputStream(imageBytes))
              .size(800, 600)
              .outputFormat("jpg")
              .outputQuality(0.85)
              .toOutputStream(os);
        }

        byte[] finalBytes = os.toByteArray();
        if (finalBytes.length == 0) return null;

        String filename = "news-" + UUID.randomUUID() + "." + extension;
        return fileStorageService.storeFile(
            new ByteArrayInputStream(finalBytes), filename, contentType);
      }
    } catch (Exception e) {
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
    }
  }
}
