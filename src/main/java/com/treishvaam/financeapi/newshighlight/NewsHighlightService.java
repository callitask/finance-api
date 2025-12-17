package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.newshighlight.dto.NewsDataArticle;
import com.treishvaam.financeapi.newshighlight.dto.NewsDataResponse;
import com.treishvaam.financeapi.service.FileStorageService;
import java.awt.image.BufferedImage;
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
import javax.imageio.ImageIO;
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
      // FIX: Use existsByLink instead of existsByUrl
      if (repository.existsByLink(apiArticle.getLink())) {
        return false; // Skip duplicate
      }

      NewsHighlight news = new NewsHighlight();
      news.setTitle(apiArticle.getTitle());
      // FIX: Use setLink instead of setUrl
      news.setLink(apiArticle.getLink());
      news.setSource(formatSourceName(apiArticle.getSourceId()));
      // FIX: Use setDescription instead of setSummary
      news.setDescription(apiArticle.getDescription());

      // Handle Date
      if (apiArticle.getPubDate() != null) {
        try {
          // NewsData sends "2024-12-16 14:30:00"
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
          news.setPublishedAt(LocalDateTime.parse(apiArticle.getPubDate(), formatter));
        } catch (Exception e) {
          news.setPublishedAt(LocalDateTime.now());
        }
      } else {
        news.setPublishedAt(LocalDateTime.now());
      }

      // 5. Image Intelligence (Scrape -> Download -> Fallback)
      String bestImageUrl = resolveBestImage(apiArticle);
      news.setImageUrl(bestImageUrl);

      news.setArchived(false); // Active by default

      repository.save(news);
      return true;

    } catch (DataIntegrityViolationException e) {
      // Swallow duplicate entry errors to keep logs clean
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

    // Step B: If we found a URL (either from API or Scraper), try to download/cache it
    if (candidateUrl != null && !candidateUrl.isEmpty()) {
      String localFilename = downloadAndOptimizeImage(candidateUrl);
      if (localFilename != null) {
        return localFilename; // Return "news-xyz.webp"
      }
      return candidateUrl; // Fallback: Return remote URL "https://..."
    }

    return null; // No image found anywhere
  }

  private String scrapeImageFromUrl(String articleUrl) {
    try {
      Document doc =
          Jsoup.connect(articleUrl)
              .userAgent(
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .timeout(3000)
              .get();

      // Try og:image
      Element metaOgImage = doc.selectFirst("meta[property=og:image]");
      if (metaOgImage != null) return metaOgImage.attr("content");

      // Try twitter:image
      Element metaTwitterImage = doc.selectFirst("meta[name=twitter:image]");
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
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(3000);

      try (InputStream in = connection.getInputStream()) {
        BufferedImage image = ImageIO.read(in);
        if (image == null) return null;

        // Resize to 800px width (optimize storage)
        int targetWidth = 800;
        int targetHeight = (int) (image.getHeight() * ((double) targetWidth / image.getWidth()));
        BufferedImage resized =
            new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        resized.createGraphics().drawImage(image, 0, 0, targetWidth, targetHeight, null);

        // Convert to WebP
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(resized, "webp", os);

        String filename = "news-" + UUID.randomUUID() + ".webp";
        return fileStorageService.storeFile(
            new ByteArrayInputStream(os.toByteArray()), filename, "image/webp");
      }
    } catch (Exception e) {
      // If download fails, return null so we can fallback to the remote URL
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
    // Capitalize words: "bloomberg" -> "Bloomberg"
    return Arrays.stream(sourceId.split(" "))
        .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }

  private void archiveOldStories() {
    // Keep strictly the top 50 freshest stories active
    // FIX: Now uses the new List method in Repository
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
