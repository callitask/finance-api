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
import java.time.temporal.ChronoUnit;
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
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * SitemapGenerationService
 *
 * <p>Generates: - /sitemaps/static.xml - /sitemaps/categories.xml - /sitemaps/posts-&lt;n&gt;.xml
 * (paged) - /sitemap.xml (index) - /sitemaps/sitemap-news.xml (Google News, last 48h) -
 * /sitemaps/feed.xml (RSS 2.0)
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

      // 1. Standard Sitemaps
      generateStaticSitemap();
      generateCategoriesSitemap();

      long totalPosts = blogPostService.countPublishedPosts();
      int totalPostPages = (int) Math.ceil((double) totalPosts / SITEMAP_PAGE_SIZE);
      if (totalPostPages == 0) totalPostPages = 1;

      for (int i = 0; i < totalPostPages; i++) {
        generatePostsSitemap(i, SITEMAP_PAGE_SIZE);
      }

      // 2. Enterprise SEO (News & RSS)
      generateNewsSitemap();
      generateRssFeed();

      // 3. Index (Must be last)
      generateSitemapIndex(totalPostPages);
      logger.info("Sitemap generation task finished successfully.");

    } catch (Exception e) {
      logger.error("Failed to generate sitemaps", e);
    }
  }

  private void generateNewsSitemap() throws IOException {
    StringBuilder sitemap = new StringBuilder();
    sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
    sitemap.append("        xmlns:news=\"http://www.google.com/schemas/sitemap-news/0.9\">\n");

    // Google News only accepts articles from the last 48 hours
    Instant fortyEightHoursAgo = Instant.now().minus(48, ChronoUnit.HOURS);

    List<BlogPost> recentPosts =
        blogPostService.findAllPublishedPosts(Pageable.unpaged()).getContent().stream()
            .filter(p -> p.getCreatedAt().isAfter(fortyEightHoursAgo))
            .collect(Collectors.toList());

    Map<String, String> categorySlugMap = getCategorySlugMap();

    for (BlogPost post : recentPosts) {
      String url = buildPostUrl(post, categorySlugMap);
      String date =
          post.getCreatedAt()
              .atZone(ZoneId.of("UTC"))
              .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      String title = escapeXml(post.getTitle());
      String keywords = post.getKeywords() != null ? escapeXml(post.getKeywords()) : "";

      sitemap.append("  <url>\n");
      sitemap.append("    <loc>").append(url).append("</loc>\n");
      sitemap.append("    <news:news>\n");
      sitemap.append("      <news:publication>\n");
      sitemap.append("        <news:name>Treishvaam Finance</news:name>\n");
      sitemap.append("        <news:language>en</news:language>\n");
      sitemap.append("      </news:publication>\n");
      sitemap
          .append("      <news:publication_date>")
          .append(date)
          .append("</news:publication_date>\n");
      sitemap.append("      <news:title>").append(title).append("</news:title>\n");
      if (!keywords.isEmpty()) {
        sitemap.append("      <news:keywords>").append(keywords).append("</news:keywords>\n");
      }
      sitemap.append("    </news:news>\n");
      sitemap.append("  </url>\n");
    }

    sitemap.append("</urlset>\n");
    writeToFile("sitemap-news.xml", sitemap.toString());
    logger.info("Generated sitemap-news.xml with {} items.", recentPosts.size());
  }

  private void generateRssFeed() throws IOException {
    StringBuilder rss = new StringBuilder();
    rss.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    rss.append(
        "<rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\""
            + " xmlns:atom=\"http://www.w3.org/2005/Atom\">\n");
    rss.append("  <channel>\n");
    rss.append("    <title>Treishvaam Finance</title>\n");
    rss.append("    <link>").append(baseUrl).append("</link>\n");
    rss.append(
        "    <description>Expert financial analysis, market news, and insights.</description>\n");
    rss.append("    <language>en-us</language>\n");
    rss.append("    <atom:link href=\"")
        .append(baseUrl)
        .append("/feed.xml\" rel=\"self\" type=\"application/rss+xml\" />\n");

    // RSS Feed usually contains the last 20-50 items
    Page<BlogPost> posts = blogPostService.findAllPublishedPosts(PageRequest.of(0, 50));
    Map<String, String> categorySlugMap = getCategorySlugMap();
    DateTimeFormatter rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("UTC"));

    for (BlogPost post : posts) {
      String url = buildPostUrl(post, categorySlugMap);
      String pubDate = rfc1123.format(post.getCreatedAt());

      rss.append("    <item>\n");
      rss.append("      <title>").append(escapeXml(post.getTitle())).append("</title>\n");
      rss.append("      <link>").append(url).append("</link>\n");
      rss.append("      <guid isPermaLink=\"true\">").append(url).append("</guid>\n");
      rss.append("      <pubDate>").append(pubDate).append("</pubDate>\n");
      rss.append("      <description>")
          .append(
              escapeXml(
                  post.getMetaDescription() != null ? post.getMetaDescription() : post.getTitle()))
          .append("</description>\n");

      // Content Encoded for syndicators like Flipboard
      if (post.getContent() != null) {
        rss.append("      <content:encoded><![CDATA[")
            .append(post.getContent())
            .append("]]></content:encoded>\n");
      }

      rss.append("    </item>\n");
    }

    rss.append("  </channel>\n");
    rss.append("</rss>\n");
    writeToFile("feed.xml", rss.toString());
    logger.info("Generated feed.xml (RSS 2.0).");
  }

  private void generateSitemapIndex(int totalPostPages) throws IOException {
    StringBuilder index = new StringBuilder();
    index.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    index.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    String now =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.now().atZone(ZoneId.of("UTC")));

    index.append(createSitemapEntry(normalizeUrl(baseUrl + "/sitemaps/static.xml"), now));
    index.append(createSitemapEntry(normalizeUrl(baseUrl + "/sitemaps/categories.xml"), now));

    // Add News Sitemap to Index
    index.append(createSitemapEntry(normalizeUrl(baseUrl + "/sitemaps/sitemap-news.xml"), now));

    for (int i = 0; i < totalPostPages; i++) {
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

    Map<String, String> categorySlugMap = getCategorySlugMap();

    Page<BlogPost> posts = blogPostService.findAllPublishedPosts(PageRequest.of(page, pageSize));
    if (posts == null || posts.isEmpty()) {
      logger.info(
          "No posts found for page {} (pageSize {}). Writing empty sitemap file.", page, pageSize);
    } else {
      for (BlogPost post : posts) {
        String postUrl = buildPostUrl(post, categorySlugMap);
        if (postUrl == null) continue;

        String lastMod = formatLastMod(post.getUpdatedAt());
        sitemap.append(createUrlEntry(postUrl, lastMod, "weekly", "0.9"));
      }
    }

    sitemap.append("</urlset>\n");
    writeToFile(String.format("posts-%d.xml", page + 1), sitemap.toString());
  }

  // --- Helpers ---

  private Map<String, String> getCategorySlugMap() {
    return categoryRepository.findAll().stream()
        .collect(Collectors.toMap(Category::getName, Category::getSlug, (slug1, slug2) -> slug1));
  }

  private String buildPostUrl(BlogPost post, Map<String, String> categorySlugMap) {
    if (post == null
        || post.getUserFriendlySlug() == null
        || post.getUrlArticleId() == null
        || post.getCategory() == null) {
      return null;
    }
    String categorySlug =
        categorySlugMap.getOrDefault(post.getCategory().getName(), "uncategorized");

    return String.format(
        "%s/category/%s/%s/%s",
        normalizeUrl(baseUrl), categorySlug, post.getUserFriendlySlug(), post.getUrlArticleId());
  }

  private String createSitemapEntry(String loc, String lastMod) {
    return String.format(
        "  <sitemap>\n    <loc>%s</loc>\n    <lastmod>%s</lastmod>\n  </sitemap>\n",
        escapeXml(loc), escapeXml(lastMod));
  }

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

  private String formatLastMod(Instant instant) {
    if (instant == null) return LocalDateStr();
    return instant.atZone(ZoneId.of("UTC")).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private String LocalDateStr() {
    return Instant.now()
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private void writeToFile(String fileName, String content) throws IOException {
    Path filePath = Paths.get(sitemapDir, fileName);
    Files.writeString(filePath, content, StandardCharsets.UTF_8);
    logger.debug("Generated sitemap file: {}", filePath.toAbsolutePath());
  }

  private String escapeXml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private String normalizeUrl(String url) {
    if (url == null) return "";
    return url.replaceAll("(?<!(http:|https:))//+", "/");
  }
}
