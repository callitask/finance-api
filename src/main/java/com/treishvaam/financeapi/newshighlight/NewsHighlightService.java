package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.newshighlight.dto.NewsDataArticle;
import com.treishvaam.financeapi.newshighlight.dto.NewsDataResponse;
import com.treishvaam.financeapi.service.FileStorageService;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class NewsHighlightService {

  private static final Logger logger = LoggerFactory.getLogger(NewsHighlightService.class);
  private final NewsHighlightRepository repository;
  private final FileStorageService fileStorageService;
  private final RestTemplate restTemplate;

  // --- TIER 1 TRUSTED SOURCES (AllowList) ---
  private static final Set<String> TRUSTED_SOURCE_IDS =
      Set.of(
          "bloomberg",
          "reuters",
          "cnbc",
          "wsj",
          "financial_times",
          "yahoofinance",
          "marketwatch",
          "forbes",
          "businessinsider",
          "theguardian",
          "bbc",
          "techcrunch",
          "economictimes",
          "moneycontrol",
          "livemint");

  // Maximum number of news items to show on the site (Active Window)
  private static final int MAX_ACTIVE_NEWS = 50;

  @Value("${newsdata.api.key}")
  private String apiKey;

  public NewsHighlightService(
      NewsHighlightRepository repository, FileStorageService fileStorageService) {
    this.repository = repository;
    this.fileStorageService = fileStorageService;
    this.restTemplate = new RestTemplate();
  }

  // --- COLD START BOOTSTRAPPER ---
  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    if (repository.count() == 0) {
      logger.info("Fresh Database Detected. Triggering Initial News Intelligence Cycle...");
      fetchAndStoreNews();
    }
  }

  public Page<NewsHighlight> getHighlights(Pageable pageable) {
    return repository.findByIsArchivedFalseOrderByPublishedAtDesc(pageable);
  }

  // --- INTELLIGENCE CYCLE (Every 1 Hour) ---
  // 10 credits per fetch. 24 fetches/day = 240 credits max (adjusts within free tier)
  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
  @Transactional
  public void fetchAndStoreNews() {
    logger.info("Starting Enterprise News Intelligence Cycle (NewsData.io)...");

    if (apiKey == null || apiKey.contains("your_key")) {
      logger.warn("NewsData API Key is missing. Skipping fetch.");
      return;
    }

    try {
      // Fetch Business News in English
      String url =
          "https://newsdata.io/api/1/news?apikey="
              + apiKey
              + "&category=business&language=en&country=us,in,gb";

      NewsDataResponse response = restTemplate.getForObject(url, NewsDataResponse.class);

      if (response == null || !"success".equals(response.getStatus())) {
        logger.error("NewsData API returned invalid status: {}", response);
        return;
      }

      List<NewsDataArticle> articles = response.getResults();
      int savedCount = 0;

      for (NewsDataArticle article : articles) {
        // 1. Deduplication
        if (repository.existsByLink(article.getLink())
            || repository.existsByTitle(article.getTitle())) {
          continue;
        }

        // 2. Strict Source Filtering
        if (!isValidSource(article)) {
          continue;
        }

        // 3. Entity Mapping
        NewsHighlight entity = new NewsHighlight();
        entity.setTitle(cleanTitle(article.getTitle()));
        entity.setLink(article.getLink());
        entity.setDescription(article.getDescription());
        // Use source_id as source name if available
        entity.setSource(
            article.getSourceId() != null ? article.getSourceId().toUpperCase() : "NEWS");
        entity.setPublishedAt(parseDate(article.getPubDate()));
        entity.setArchived(false); // New news is always active

        // 4. Image Optimization (Self-Hosted)
        if (article.getImageUrl() != null && !article.getImageUrl().isEmpty()) {
          // Download and optimize image to prevent hotlink blocks
          String internalUrl = processAndStoreImage(article.getImageUrl());
          entity.setImageUrl(internalUrl != null ? internalUrl : null);
        } else {
          entity.setImageUrl(null);
        }

        repository.save(entity);
        savedCount++;
      }

      logger.info(
          "Intelligence Cycle Complete. Fetched: {}, Indexed: {}", articles.size(), savedCount);

      // 5. Enforce Active Window (Auto-Archival)
      enforceActiveWindow();

    } catch (Exception e) {
      logger.error("News Ingestion Failed: {}", e.getMessage());
    }
  }

  private boolean isValidSource(NewsDataArticle article) {
    if (article.getSourceId() == null) return false;
    // Check if the source_id matches our trusted list (partial match allowed for safety)
    String sourceId = article.getSourceId().toLowerCase();
    for (String trusted : TRUSTED_SOURCE_IDS) {
      if (sourceId.contains(trusted)) return true;
    }
    return false;
  }

  private String cleanTitle(String title) {
    if (title == null) return "Market Update";
    // Remove generic suffixes often found in titles
    return title.split(" - ")[0];
  }

  /** Ensures we only keep MAX_ACTIVE_NEWS (50) visible. Everything else is marked as archived. */
  private void enforceActiveWindow() {
    long activeCount = repository.countByIsArchivedFalse();
    if (activeCount > MAX_ACTIVE_NEWS) {
      long toArchiveCount = activeCount - MAX_ACTIVE_NEWS;
      // Find the oldest active ones
      List<NewsHighlight> oldNews =
          repository.findOldestActive(PageRequest.of(0, (int) toArchiveCount));

      for (NewsHighlight news : oldNews) {
        news.setArchived(true);
      }
      repository.saveAll(oldNews);
      logger.info("Auto-Archived {} old news articles.", oldNews.size());
    }
  }

  private LocalDateTime parseDate(String dateStr) {
    if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
    try {
      // NewsData.io format: "2022-11-16 14:32:00"
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      return LocalDateTime.parse(dateStr, formatter);
    } catch (Exception e) {
      return LocalDateTime.now();
    }
  }

  private String processAndStoreImage(String externalUrl) {
    try {
      URL url = new URL(externalUrl);
      BufferedImage originalImage = ImageIO.read(url);
      if (originalImage == null) return null;

      int targetWidth = 800;
      int originalWidth = originalImage.getWidth();
      int originalHeight = originalImage.getHeight();

      if (originalWidth > targetWidth) {
        double ratio = (double) targetWidth / originalWidth;
        originalHeight = (int) (originalHeight * ratio);
      } else {
        targetWidth = originalWidth;
      }

      BufferedImage resizedImage =
          new BufferedImage(targetWidth, originalHeight, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = resizedImage.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(originalImage, 0, 0, targetWidth, originalHeight, null);
      g.dispose();

      ByteArrayOutputStream os = new ByteArrayOutputStream();
      Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
      if (!writers.hasNext()) return null;

      ImageWriter writer = writers.next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.70f); // Good compression for thumbnails

      try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(resizedImage, null, null), param);
      }
      writer.dispose();

      String filename = "news-" + UUID.randomUUID() + ".jpg";
      return fileStorageService.storeFile(
          new ByteArrayInputStream(os.toByteArray()), filename, "image/jpeg");

    } catch (Exception e) {
      // If image download fails, return null so we just don't have an image (better than crashing)
      return null;
    }
  }
}
