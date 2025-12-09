package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.service.FileStorageService;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate; // Added for API call

@Service
public class NewsHighlightService {

  private static final Logger logger = LoggerFactory.getLogger(NewsHighlightService.class);
  private final NewsHighlightRepository repository;
  private final FileStorageService fileStorageService;
  private final RestTemplate restTemplate;

  @Value("${market.data.api.key}")
  private String apiKey;

  public NewsHighlightService(
      NewsHighlightRepository repository, FileStorageService fileStorageService) {
    this.repository = repository;
    this.fileStorageService = fileStorageService;
    this.restTemplate = new RestTemplate();
  }

  public List<NewsHighlight> getLatestHighlights() {
    return repository.findTop10ByOrderByPublishedAtDesc();
  }

  @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
  @Transactional
  public void fetchAndStoreNews() {
    logger.info("Starting Enterprise News Ingestion...");
    try {
      // 1. Fetch from FMP API
      String url =
          "https://financialmodelingprep.com/api/v3/fmp/articles?page=0&size=10&apikey=" + apiKey;
      NewsArticleDto[] response = restTemplate.getForObject(url, NewsArticleDto[].class);

      List<NewsArticleDto> articles =
          (response != null) ? Arrays.asList(response) : new ArrayList<>();

      for (NewsArticleDto article : articles) {
        if (repository.existsByTitle(article.getTitle())) continue;

        NewsHighlight entity = new NewsHighlight();
        entity.setTitle(article.getTitle());
        entity.setLink(article.getLink());
        entity.setSource(article.getSource());
        entity.setPublishedAt(article.getDate());

        // --- IMAGE OPTIMIZATION PIPELINE ---
        String rawImageUrl = article.getImage();
        if (rawImageUrl == null || rawImageUrl.isEmpty()) {
          rawImageUrl = scrapeImageFromArticle(article.getLink());
        }

        if (rawImageUrl != null && !rawImageUrl.isEmpty()) {
          // Optimized: Resize -> Compress -> Internal Store
          String internalUrl = processAndStoreImage(rawImageUrl);
          entity.setImageUrl(internalUrl != null ? internalUrl : rawImageUrl);
        } else {
          entity.setImageUrl(null);
        }

        repository.save(entity);
      }
    } catch (Exception e) {
      logger.error("News Ingestion Failed: {}", e.getMessage());
    }
  }

  private String processAndStoreImage(String externalUrl) {
    try {
      URL url = new URL(externalUrl);
      BufferedImage originalImage = ImageIO.read(url);
      if (originalImage == null) return null;

      // Resize Logic
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
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(originalImage, 0, 0, targetWidth, originalHeight, null);
      g.dispose();

      // Compress Logic
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
      if (!writers.hasNext()) return null;

      ImageWriter writer = writers.next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.75f);

      try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(resizedImage, null, null), param);
      }
      writer.dispose();

      // Store Logic
      String filename = "news-" + UUID.randomUUID() + ".jpg";
      // Calls the OVERLOADED method in FileStorageService
      return fileStorageService.storeFile(
          new ByteArrayInputStream(os.toByteArray()), filename, "image/jpeg");

    } catch (Exception e) {
      logger.warn("Image Optimization Skipped: {}", e.getMessage());
      return null;
    }
  }

  private String scrapeImageFromArticle(String articleUrl) {
    try {
      Document doc =
          Jsoup.connect(articleUrl)
              .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
              .timeout(3000)
              .get();
      Element og = doc.selectFirst("meta[property=og:image]");
      return (og != null) ? og.attr("content") : null;
    } catch (IOException e) {
      return null;
    }
  }
}
