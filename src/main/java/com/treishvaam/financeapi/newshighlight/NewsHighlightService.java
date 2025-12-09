package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.service.FileStorageService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class NewsHighlightService {

    private static final Logger logger = LoggerFactory.getLogger(NewsHighlightService.class);

    private final NewsHighlightRepository repository;
    private final FileStorageService fileStorageService;

    @Value("${market.data.api.key}")
    private String apiKey;

    public NewsHighlightService(NewsHighlightRepository repository, FileStorageService fileStorageService) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
    }

    public List<NewsHighlight> getLatestHighlights() {
        return repository.findTop10ByOrderByPublishedAtDesc();
    }

    /**
     * Enterprise Scheduled Task:
     * Fetches news -> Deduplicates -> Extracts Image -> Optimizes Image -> Stores
     */
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void fetchAndStoreNews() {
        logger.info("Starting Enterprise News Ingestion...");
        try {
            // 1. Fetch JSON from External API (Example: FMP)
            String url = "https://financialmodelingprep.com/api/v3/fmp/articles?page=0&size=10&apikey=" + apiKey;
            String jsonResponse = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .header("Accept", "application/json")
                    .execute()
                    .body();

            // Note: In your actual code, use your existing ObjectMapper logic here to parse 'jsonResponse' into DTOs.
            // For this implementation, I am iterating over the logic you need to insert.
            List<NewsArticleDto> articles = new ArrayList<>(); // REPLACE with actual parsed list

            for (NewsArticleDto article : articles) {
                if (repository.existsByTitle(article.getTitle())) {
                    continue;
                }

                NewsHighlight entity = new NewsHighlight();
                entity.setTitle(article.getTitle());
                entity.setLink(article.getLink());
                entity.setSource(article.getSource());
                entity.setPublishedAt(article.getDate());

                // --- ENTERPRISE IMAGE PIPELINE ---
                String rawImageUrl = article.getImage();
                
                // A. Fallback: If API has no image, try to scrape it via OG Tags
                if (rawImageUrl == null || rawImageUrl.isEmpty()) {
                    rawImageUrl = scrapeImageFromArticle(article.getLink());
                }

                // B. Optimization: Download, Resize & Compress
                if (rawImageUrl != null && !rawImageUrl.isEmpty()) {
                    String internalUrl = processAndStoreImage(rawImageUrl);
                    entity.setImageUrl(internalUrl != null ? internalUrl : rawImageUrl);
                } else {
                    entity.setImageUrl(null); // Frontend will handle placeholder
                }
                // ---------------------------------

                repository.save(entity);
            }
            logger.info("News Ingestion Complete.");

        } catch (Exception e) {
            logger.error("News Ingestion Failed: {}", e.getMessage());
        }
    }

    /**
     * Downloads an external image, resizes it to max 800px width,
     * compresses it to 75% quality JPEG, and uploads to MinIO.
     */
    private String processAndStoreImage(String externalUrl) {
        try {
            URL url = new URL(externalUrl);
            BufferedImage originalImage = ImageIO.read(url);
            if (originalImage == null) return null;

            // 1. Calculate Resize Dimensions (Max Width: 800px)
            int targetWidth = 800;
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            // Only resize if the image is actually larger than our target
            if (originalWidth <= targetWidth) {
                targetWidth = originalWidth;
            } else {
                // Maintain Aspect Ratio
                double ratio = (double) targetWidth / originalWidth;
                originalHeight = (int) (originalHeight * ratio);
            }

            // 2. High-Quality Scaling
            BufferedImage resizedImage = new BufferedImage(targetWidth, originalHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(originalImage, 0, 0, targetWidth, originalHeight, null);
            g.dispose();

            // 3. Compress to JPEG (75% Quality)
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.75f); // 75% Quality = Great balance of size/speed

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(resizedImage, null, null), param);
            }
            writer.dispose();

            // 4. Upload to MinIO
            byte[] imageBytes = os.toByteArray();
            String filename = "news-" + UUID.randomUUID() + ".jpg";
            // Uses your existing FileStorageService
            return fileStorageService.storeFile(new ByteArrayInputStream(imageBytes), filename, "image/jpeg");

        } catch (Exception e) {
            logger.warn("Image Optimization skipped for URL {}: {}", externalUrl, e.getMessage());
            return null; // Return null so we can fall back to the raw URL or placeholder
        }
    }

    private String scrapeImageFromArticle(String articleUrl) {
        try {
            Document doc = Jsoup.connect(articleUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(3000)
                    .get();
            
            Element ogImage = doc.selectFirst("meta[property=og:image]");
            if (ogImage != null) return ogImage.attr("content");
            
            Element twitterImage = doc.selectFirst("meta[name=twitter:image]");
            if (twitterImage != null) return twitterImage.attr("content");

            return null;
        } catch (IOException e) {
            return null;
        }
    }
}