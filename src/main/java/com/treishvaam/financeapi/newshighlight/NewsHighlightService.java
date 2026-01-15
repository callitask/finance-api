package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.newshighlight.dto.NewsDataArticle;
import com.treishvaam.financeapi.newshighlight.dto.NewsDataResponse;
import com.treishvaam.financeapi.service.FileStorageService;
import com.treishvaam.financeapi.service.ImageService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
  private final FileStorageService fileStorageService; // Kept for legacy healing if needed
  private final ImageService imageService; // NEW: Enterprise Image Authority
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
      NewsHighlightRepository repository,
      FileStorageService fileStorageService,
      ImageService imageService) {
    this.repository = repository;
    this.fileStorageService = fileStorageService;
    this.imageService = imageService;
    this.restTemplate = new RestTemplate();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (repository.count() == 0) {
      logger.info("📰 News DB Empty. Triggering initial fetch...");
      fetchAndStoreNews();
    } else {
      healLegacyImages();
    }
  }

  public Page<NewsHighlight> getHighlights(Pageable pageable) {
    return repository.findByIsArchivedFalseOrderByPublishedAtDesc(pageable);
  }

  // --- Image Healer (Runs every 15 mins) ---
  @Scheduled(fixedRate = 900000)
  public void healLegacyImages() {
    // Only checks for broken links, doesn't re-download healthy legacy images
    // to avoid slamming the ImageService with 1000s of requests on startup.
    Pageable limit = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "publishedAt"));
    List<NewsHighlight> recentNews =
        repository.findByIsArchivedFalseOrderByPublishedAtDesc(limit).getContent();

    for (NewsHighlight news : recentNews) {
      if (news.getImageUrl() == null || news.getImageUrl().isEmpty()) {
        repairSingleNewsItem(news);
      }
    }
  }

  private void repairSingleNewsItem(NewsHighlight news) {
    try {
      String scrapedImageUrl = scrapeImageFromUrl(news.getLink());
      if (scrapedImageUrl != null && !scrapedImageUrl.isEmpty()) {
        String newLocalPath = downloadAndOptimizeImage(scrapedImageUrl);
        if (newLocalPath != null) {
          news.setImageUrl(newLocalPath);
          repository.save(news);
        }
      }
    } catch (Exception e) {
      /* Silent fail */
    }
  }

  @Scheduled(fixedRate = 900000)
  public void fetchAndStoreNews() {
    logger.info("🌍 Starting News Fetch Cycle...");
    try {
      String url =
          "https://newsdata.io/api/1/news?apikey=" + apiKey + "&category=business&language=en";
      ResponseEntity<NewsDataResponse> response =
          restTemplate.getForEntity(url, NewsDataResponse.class);

      if (response.getBody() == null || response.getBody().getResults() == null) return;

      List<NewsDataArticle> articles = response.getBody().getResults();
      int newCount = 0;

      for (NewsDataArticle article : articles) {
        if (!isAllowedSource(article.getSourceId())) continue;
        if (saveArticleSafe(article)) newCount++;
      }
      logger.info("✅ Cycle Complete. New Items: {}", newCount);
      archiveOldStories();
    } catch (Exception e) {
      logger.error("❌ News Fetch Cycle Error: {}", e.getMessage());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean saveArticleSafe(NewsDataArticle apiArticle) {
    try {
      String rawLink = apiArticle.getLink();
      if (rawLink == null) return false;
      String normalizedLink = rawLink.trim();

      if (repository.existsByLink(normalizedLink)) return false;
      if (apiArticle.getTitle() != null && repository.existsByTitle(apiArticle.getTitle()))
        return false;

      NewsHighlight news = new NewsHighlight();
      news.setTitle(apiArticle.getTitle());
      news.setLink(normalizedLink);
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

      // Enterprise Image Processing
      String bestImageUrl = resolveBestImage(apiArticle);
      news.setImageUrl(bestImageUrl);
      news.setArchived(false);

      repository.save(news);
      return true;

    } catch (DataIntegrityViolationException e) {
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
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)")
              .timeout(5000)
              .get();
      Element metaOg = doc.selectFirst("meta[property='og:image']");
      if (metaOg != null) return metaOg.attr("content");
    } catch (Exception e) {
      /* Ignore */
    }
    return null;
  }

  /**
   * REFACTORED: Now delegates to ImageService for Multi-Variant generation. Uses prefix 'news-mv-'
   * to distinguish Enterprise News from Legacy News.
   */
  private String downloadAndOptimizeImage(String imageUrl) {
    try {
      URL url = new URL(imageUrl);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("User-Agent", "Mozilla/5.0");
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

        // ENTERPRISE INTEGRATION: Call ImageService
        // Prefix: "news-mv-" (News Multi-Variant)
        // Profile: NEWS (Aggressive compression)
        ImageService.ImageMetadataDto metadata =
            imageService.processImage(
                imageBytes, "news-mv-", ImageService.OptimizationProfile.NEWS);

        // Return the simple filename (e.g., "news-mv-uuid.webp")
        // The FileController expects the file to be in the standard uploads directory.
        // ImageService.saveImageAndGetMetadata/processImage handles the storage to that directory.
        return "api/v1/uploads/" + metadata.getFullPath();
      }
    } catch (Exception e) {
      // Fallback: If download fails, return null so we use the external URL or placeholder
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
