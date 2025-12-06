package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.repository.CategoryRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * SitemapGenerationService
 *
 * <p>Generates: - /sitemaps/static.xml - /sitemaps/categories.xml - /sitemaps/posts-<n>.xml (paged)
 * - /sitemap.xml (index referencing the above, with <lastmod> timestamps)
 *
 * <p>Runs once on application ready and then every 3 hours (cron).
 */
@Service
public class SitemapGenerationService {

  private static final Logger logger = LoggerFactory.getLogger(SitemapGenerationService.class);
  private static final int SITEMAP_PAGE_SIZE = 1000;
  private static final List<String> STATIC_PAGES = List.of("/", "/about", "/vision", "/contact");

  @Value("${storage.sitemap-dir}")
  private String sitemapDir;

  @Value("${app.base-url}")
  private String baseUrl;

  @Autowired private BlogPostService blogPostService;

  @Autowired private CategoryRepository categoryRepository;

  /** Trigger sitemap generation on application startup. */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    logger.info("Server started. Triggering immediate sitemap generation...");
    generateSitemaps();
  }

  /**
   * Scheduled task to regenerate sitemaps every 3 hours. Spring cron format: second minute hour day
   * month day-of-week
   */
  @Scheduled(cron = "0 0 */3 * * *")
  public void generateSitemaps() {
    logger.info("Starting sitemap generation task...");
    try {
      Path sitemapPath = Paths.get(sitemapDir);
      if (!Files.exists(sitemapPath)) {
        Files.createDirectories(sitemapPath);
        logger.info("Created sitemap directory: {}", sitemapPath.toAbsolutePath());
      }

      // Generate content sitemaps first
      generateStaticSitemap();
      generateCategoriesSitemap();

      long totalPosts = blogPostService.countPublishedPosts();
      int totalPostPages = (int) Math.ceil((double) totalPosts / SITEMAP_PAGE_SIZE);
      if (totalPostPages == 0) totalPostPages = 1;

      for (int i = 0; i < totalPostPages; i++) {
        generatePostsSitemap(i, SITEMAP_PAGE_SIZE);
      }

      // Generate the Index last, ensuring it points to the fresh files with a fresh timestamp
      generateSitemapIndex(totalPostPages);
      logger.info("Sitemap generation task finished successfully.");

    } catch (Exception e) {
      logger.error("Failed to generate sitemaps", e);
    }
  }

  private void generateSitemapIndex(int totalPostPages) throws IOException {
    StringBuilder index = new StringBuilder();
    index.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    index.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    // Use full offset timestamp for lastmod (ISO_OFFSET_DATE_TIME)
    String now =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.now().atZone(ZoneId.of("UTC")));

    index.append(createSitemapEntry(normalizeUrl(baseUrl + "/sitemaps/static.xml"), now));
    index.append(createSitemapEntry(normalizeUrl(baseUrl + "/sitemaps/categories.xml"), now));

    for (int i = 0; i < totalPostPages; i++) {
      // posts files are 1-indexed in name (posts-1.xml, posts-2.xml, ...)
      index.append(
          createSitemapEntry(
              String.format("%s/sitemaps/posts-%d.xml", normalizeUrl(baseUrl), i + 1), now));
    }

    index.append("</sitemapindex>\n");
    writeToFile("sitemap.xml", index.toString());
  }

  private void generateStaticSitemap() throws IOException {
    StringBuilder sitemap = new StringBuilder();
    sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    String today = LocalDateStr();

    for (String page : STATIC_PAGES) {
      sitemap.append(createUrlEntry(normalizeUrl(baseUrl + page), today, "daily", "1.0"));
    }

    sitemap.append("</urlset>\n");
    writeToFile("static.xml", sitemap.toString());
  }

  private void generateCategoriesSitemap() throws IOException {
    StringBuilder sitemap = new StringBuilder();
    sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    String today = LocalDateStr();

    List<Category> categories = categoryRepository.findAll();
    for (Category category : categories) {
      if (category == null || category.getSlug() == null || category.getSlug().isBlank()) continue;
      String catUrl = String.format("%s/category/%s", normalizeUrl(baseUrl), category.getSlug());
      sitemap.append(createUrlEntry(catUrl, today, "weekly", "0.8"));
    }

    sitemap.append("</urlset>\n");
    writeToFile("categories.xml", sitemap.toString());
  }

  private void generatePostsSitemap(int page, int pageSize) throws IOException {
    StringBuilder sitemap = new StringBuilder();
    sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    Map<String, String> categorySlugMap =
        categoryRepository.findAll().stream()
            .collect(
                Collectors.toMap(Category::getName, Category::getSlug, (slug1, slug2) -> slug1));

    Page<BlogPost> posts = blogPostService.findAllPublishedPosts(PageRequest.of(page, pageSize));
    if (posts == null || posts.isEmpty()) {
      // no posts on this page - still write an empty sitemap with header/footer
      logger.info(
          "No posts found for page {} (pageSize {}). Writing empty sitemap file.", page, pageSize);
    } else {
      for (BlogPost post : posts) {
        if (post == null
            || post.getUserFriendlySlug() == null
            || post.getUrlArticleId() == null
            || post.getCategory() == null) {
          continue;
        }

        String categorySlug =
            categorySlugMap.getOrDefault(post.getCategory().getName(), "uncategorized");

        String postUrl =
            String.format(
                "%s/category/%s/%s/%s",
                normalizeUrl(baseUrl),
                categorySlug,
                post.getUserFriendlySlug(),
                post.getUrlArticleId());

        String lastMod = formatLastMod(post.getUpdatedAt());
        sitemap.append(createUrlEntry(postUrl, lastMod, "weekly", "0.9"));
      }
    }

    sitemap.append("</urlset>\n");
    writeToFile(String.format("posts-%d.xml", page + 1), sitemap.toString());
  }

  /** Create a sitemapindex <sitemap> entry with lastmod */
  private String createSitemapEntry(String loc, String lastMod) {
    return String.format(
        "  <sitemap>\n    <loc>%s</loc>\n    <lastmod>%s</lastmod>\n  </sitemap>\n",
        escapeXml(loc), escapeXml(lastMod));
  }

  /** Create a <url> entry for the urlset */
  private String createUrlEntry(String loc, String lastmod, String changefreq, String priority) {
    StringBuilder urlEntry = new StringBuilder();
    urlEntry.append("  <url>\n");
    urlEntry.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
    if (lastmod != null && !lastmod.isEmpty()) {
      urlEntry.append("    <lastmod>").append(escapeXml(lastmod)).append("</lastmod>\n");
    }
    urlEntry.append("    <changefreq>").append(escapeXml(changefreq)).append("</changefreq>\n");
    urlEntry.append("    <priority>").append(escapeXml(priority)).append("</priority>\n");
    urlEntry.append("  </url>\n");
    return urlEntry.toString();
  }

  /**
   * Format Instant -> ISO_LOCAL_DATE (YYYY-MM-DD) for <lastmod> on individual URLs. We use
   * date-only for per-URL lastmod which is acceptable to Google.
   */
  private String formatLastMod(Instant instant) {
    if (instant == null) return LocalDateStr();
    return instant.atZone(ZoneId.of("UTC")).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  /** Today's date as YYYY-MM-DD */
  private String LocalDateStr() {
    return Instant.now()
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  /** Writes content to sitemapDir/fileName using UTF-8 and truncates existing. */
  private void writeToFile(String fileName, String content) throws IOException {
    Path filePath = Paths.get(sitemapDir, fileName);
    Files.writeString(filePath, content, StandardCharsets.UTF_8);
    logger.debug("Generated sitemap file: {}", filePath.toAbsolutePath());
  }

  /** Basic XML escaping for values used inside tags. */
  private String escapeXml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  /** Ensure no double-slash when concatenating baseUrl with paths. */
  private String normalizeUrl(String url) {
    if (url == null) return "";
    // replace // (except the protocol part "https://")
    return url.replaceAll("(?<!(http:|https:))//+", "/");
  }
}
