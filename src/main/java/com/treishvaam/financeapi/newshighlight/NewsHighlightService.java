/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - News ingestion, scraping, image optimization, and archival service.
 *
 * <p>Security Constraints: - Outbound scraping must enforce HTTPS + SSRF protections and bounded
 * streams (CWE-918/CWE-400).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Security Remediation - SSRF & Stream Cap -
 * 2026-10-27): Added isSafeExternalHttpsUrl validation, redirect blocking, and 5 MB stream ceiling.
 *
 * <p>- EDITED (Security Remediation - News API Key Redaction - 2026-10-28): Added redactApiKey
 * helper and applied it to fetchAndStoreNews exception logging (CWE-532).
 *
 * <p>- EDITED (Phase 6 - Resolve Best Image Fallback & Relative Path Fix - 2026-10-31): Fixed
 * resolveBestImage to not return raw unsafe candidateUrl when download fails; added https/http
 * scheme validation for normalizedLink in saveArticleSafe; changed downloadAndOptimizeImage return
 * to leading-slash "/api/v1/uploads/" path.
 *
 * <p>- DO-NOT-DELETE RULE (ABSOLUTE): This IMMUTABLE CHANGE HISTORY section acts as the
 * institutional memory for future AI sessions. It must never be deleted, truncated, rewritten, or
 * regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.newshighlight;

import com.treishvaam.financeapi.newshighlight.dto.NewsDataArticle;
import com.treishvaam.financeapi.newshighlight.dto.NewsDataResponse;
import com.treishvaam.financeapi.service.FileStorageService;
import com.treishvaam.financeapi.service.ImageService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
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

    private static final int MAX_IMAGE_BYTES =
            5 * 1024 * 1024; // 5 MB ceiling to protect 256MB JVM heap

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
                    "https://newsdata.io/api/1/news?apikey="
                            + apiKey
                            + "&category=business&language=en";
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
            logger.error("❌ News Fetch Cycle Error: {}", redactApiKey(e.getMessage()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveArticleSafe(NewsDataArticle apiArticle) {
        try {
            String rawLink = apiArticle.getLink();
            if (rawLink == null) return false;
            String normalizedLink = rawLink.trim();
            if (!normalizedLink.startsWith("https://") && !normalizedLink.startsWith("http://")) {
                return false;
            }
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
                    DateTimeFormatter formatter =
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
            if (localFilename != null) {
                return localFilename;
            }
            return isSafeExternalHttpsUrl(candidateUrl) ? candidateUrl.trim() : null;
        }
        return null;
    }

    private boolean isSafeExternalHttpsUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host)) {
                return false;
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress()
                        || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    return false;
                }
                byte[] rawBytes = addr.getAddress();
                if (rawBytes.length == 4) {
                    int b0 = rawBytes[0] & 0xFF;
                    int b1 = rawBytes[1] & 0xFF;
                    if (b0 == 0
                            || (b0 == 100 && b1 >= 64 && b1 <= 127)
                            || (b0 == 169 && b1 == 254)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String redactApiKey(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = input.replaceAll("(?i)(apikey=)[^&\\s]+", "$1[REDACTED]");
        if (apiKey != null && !apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED]");
        }
        return sanitized;
    }

    private String scrapeImageFromUrl(String articleUrl) {
        if (!isSafeExternalHttpsUrl(articleUrl)) {
            return null;
        }
        try {
            Document doc =
                    Jsoup.connect(articleUrl)
                            .userAgent(
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)")
                            .timeout(5000)
                            .maxBodySize(2 * 1024 * 1024)
                            .followRedirects(false)
                            .get();
            Element metaOg = doc.selectFirst("meta[property='og:image']");
            if (metaOg != null) {
                String candidate = metaOg.attr("content");
                return isSafeExternalHttpsUrl(candidate) ? candidate : null;
            }
        } catch (Exception e) {
            /* Ignore */
        }
        return null;
    }

    /**
     * REFACTORED: Now delegates to ImageService for Multi-Variant generation. Uses prefix
     * 'news-mv-' to distinguish Enterprise News from Legacy News. Enforces SSRF validation and a
     * strict 5 MB stream cap to protect the 256MB JVM heap.
     */
    private String downloadAndOptimizeImage(String imageUrl) {
        if (!isSafeExternalHttpsUrl(imageUrl)) {
            return null;
        }
        try {
            URL url = URI.create(imageUrl.trim()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int contentLength = conn.getContentLength();
            if (contentLength > MAX_IMAGE_BYTES) {
                conn.disconnect();
                return null;
            }

            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int nRead;
                int totalRead = 0;
                while ((nRead = in.read(data, 0, data.length)) != -1) {
                    totalRead += nRead;
                    if (totalRead > MAX_IMAGE_BYTES) {
                        logger.warn(
                                "Aborted news image download exceeding 5MB cap from host: {}",
                                url.getHost());
                        return null;
                    }
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] imageBytes = buffer.toByteArray();

                if (imageBytes.length == 0) return null;

                ImageService.ImageMetadataDto metadata =
                        imageService.processImage(
                                imageBytes, "news-mv-", ImageService.OptimizationProfile.NEWS);

                return "/api/v1/uploads/" + metadata.getFullPath();
            } finally {
                conn.disconnect();
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
