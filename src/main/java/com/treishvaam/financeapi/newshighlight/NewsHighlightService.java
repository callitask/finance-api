package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.newshighlight.dto.NewsDataArticle;
import com.treishvaam.financeapi.newshighlight.dto.NewsDataResponse;
import com.treishvaam.financeapi.service.FileStorageService;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements; // ADDED: Required for iterating image lists
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
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

  // Browser User-Agent to bypass anti-bot protections on images
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

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

  private static final int MAX_ACTIVE_NEWS = 50;

  @Value("${newsdata.api.key}")
  private String apiKey;

  public NewsHighlightService(
      NewsHighlightRepository repository, FileStorageService fileStorageService) {
    this.repository = repository;
    this.fileStorageService = fileStorageService;
    this.restTemplate = new RestTemplate();
  }

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

  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
  public void fetchAndStoreNews() {
    logger.info("Starting Enterprise News Intelligence Cycle (NewsData.io)...");

    if (apiKey == null || apiKey.contains("your_key")) {
      logger.warn("NewsData API Key is missing. Skipping fetch.");
      return;
    }

    try {
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
        if (repository.existsByLink(article.getLink())
            || repository.existsByTitle(article.getTitle())) {
          continue;
        }

        if (!isValidSource(article)) {
          continue;
        }

        NewsHighlight entity = new NewsHighlight();
        entity.setTitle(cleanTitle(article.getTitle()));
        entity.setLink(article.getLink());
        entity.setDescription(article.getDescription());
        entity.setSource(
            article.getSourceId() != null ? article.getSourceId().toUpperCase() : "NEWS");
        entity.setPublishedAt(parseDate(article.getPubDate()));
        entity.setArchived(false);

        // --- IMAGE STRATEGY ---
        String finalImageUrl = null;
        String sourceUrl = article.getImageUrl();

        // 1. If API didn't provide image, try to scrape it from the article
        if (sourceUrl == null || sourceUrl.isEmpty()) {
          sourceUrl = scrapeImageFromArticle(article.getLink());
        }

        // 2. If we have a URL (from API or Scraper), try to download & host it
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
          String internalUrl = processAndStoreImage(sourceUrl);

          // 3. Fallback: If internal download failed, use the external URL directly
          finalImageUrl = (internalUrl != null) ? internalUrl : sourceUrl;
        }

        entity.setImageUrl(finalImageUrl);

        try {
          repository.save(entity);
          savedCount++;
        } catch (DataIntegrityViolationException e) {
          logger.debug("Duplicate entry ignored: {}", article.getTitle());
        }
      }

      if (savedCount > 0) {
        logger.info(
            "Intelligence Cycle Complete. Fetched: {}, Indexed: {}", articles.size(), savedCount);
        enforceActiveWindow();
      } else {
        logger.info("Intelligence Cycle Complete. No new unique articles found.");
      }

    } catch (Exception e) {
      logger.error("News Ingestion Cycle Error: {}", e.getMessage());
    }
  }

  private boolean isValidSource(NewsDataArticle article) {
    if (article.getSourceId() == null) return false;
    String sourceId = article.getSourceId().toLowerCase();
    for (String trusted : TRUSTED_SOURCE_IDS) {
      if (sourceId.contains(trusted)) return true;
    }
    return false;
  }

  private String cleanTitle(String title) {
    if (title == null) return "Market Update";
    return title.split(" - ")[0];
  }

  @Transactional
  protected void enforceActiveWindow() {
    long activeCount = repository.countByIsArchivedFalse();
    if (activeCount > MAX_ACTIVE_NEWS) {
      long toArchiveCount = activeCount - MAX_ACTIVE_NEWS;
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
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      return LocalDateTime.parse(dateStr, formatter);
    } catch (Exception e) {
      return LocalDateTime.now();
    }
  }

  /**
   * Downloads an image using a Browser User-Agent, resizes it, and saves it. Returns null if
   * download fails (so we can fallback to external URL).
   */
  private String processAndStoreImage(String externalUrl) {
    try {
      URL url = new URL(externalUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestProperty("User-Agent", USER_AGENT);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      connection.connect();

      if (connection.getResponseCode() != 200) {
        logger.warn("Image Download Failed ({}): {}", connection.getResponseCode(), externalUrl);
        return null;
      }

      BufferedImage originalImage;
      try (InputStream inputStream = connection.getInputStream()) {
        originalImage = ImageIO.read(inputStream);
      }

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
      param.setCompressionQuality(0.70f);

      try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(resizedImage, null, null), param);
      }
      writer.dispose();

      String filename = "news-" + UUID.randomUUID() + ".jpg";
      return fileStorageService.storeFile(
          new ByteArrayInputStream(os.toByteArray()), filename, "image/jpeg");

    } catch (Exception e) {
      logger.warn("Image Processing Error: {}", e.getMessage());
      return null;
    }
  }

  /** * ADVANCED SCRAPER: 
   * Scrapes the article to find the 'hero' image.
   * Checks OG tags first, then scans the body content for valid images.
   */
  private String scrapeImageFromArticle(String articleUrl) {
    try {
      Document doc = Jsoup.connect(articleUrl).userAgent(USER_AGENT).timeout(5000).get();

      // 1. Try OG Image (Best Quality)
      Element ogImage = doc.selectFirst("meta[property=og:image]");
      if (ogImage != null) {
        return ogImage.attr("content");
      }

      // 2. Try Twitter Image
      Element twitterImage = doc.selectFirst("meta[name=twitter:image]");
      if (twitterImage != null) {
        return twitterImage.attr("content");
      }

      // 3. Fallback: Body Scraping (The "Hero" Search)
      // Look for images in likely content containers first to avoid sidebar ads
      Elements images = doc.select("article img, main img, .content img, .post-body img, #story-body img, img");

      for (Element img : images) {
          String src = img.attr("abs:src"); // Get absolute URL
          if (src == null || src.isEmpty()) continue;

          // Filter A: Skip SVGs or Data URIs (usually icons)
          if (src.contains("data:image") || src.endsWith(".svg")) continue;

          // Filter B: Skip common ad/tracking patterns
          String lowerSrc = src.toLowerCase();
          if (lowerSrc.contains("doubleclick") || lowerSrc.contains("pixel") || 
              lowerSrc.contains("facebook") || lowerSrc.contains("ads") || 
              lowerSrc.contains("tracker") || lowerSrc.contains("icon")) continue;

          // Filter C: Size Heuristics (Skip small icons/logos)
          // We check if width/height attributes exist and are too small (< 200px)
          String widthAttr = img.attr("width");
          String heightAttr = img.attr("height");
          
          if (isTooSmall(widthAttr) || isTooSmall(heightAttr)) continue;

          // Filter D: Skip "Logo" in alt text
          String alt = img.attr("alt").toLowerCase();
          if (alt.contains("logo")) continue;

          // If we survived all filters, this is likely the content image
          return src;
      }

      return null;
    } catch (Exception e) {
      logger.debug("Scraping failed for {}: {}", articleUrl, e.getMessage());
      return null;
    }
  }

  private boolean isTooSmall(String dimension) {
      if (dimension == null || dimension.isEmpty()) return false;
      // Extract numbers only
      String numeric = dimension.replaceAll("[^0-9]", "");
      if (numeric.isEmpty()) return false;
      try {
          return Integer.parseInt(numeric) < 200;
      } catch (NumberFormatException e) {
          return false;
      }
  }
}