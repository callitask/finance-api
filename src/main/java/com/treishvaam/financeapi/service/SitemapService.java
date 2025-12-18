package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.CategoryRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SitemapService {

  private static final Logger logger = LoggerFactory.getLogger(SitemapService.class);

  private static final int POSTS_PER_SITEMAP = 40000;

  @Value("${app.base-url:https://treishfin.treishvaamgroup.com}")
  private String baseUrl;

  @Autowired private BlogPostRepository blogPostRepository;
  @Autowired private CategoryRepository categoryRepository;

  // --- EXISTING GENERATION METHODS (Keep these as they were) ---

  @Cacheable(value = "sitemap_index", key = "'main_index'")
  public String generateSitemapIndex() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    String now = formatDate(Instant.now());

    addSitemapEntry(xml, "/sitemaps/static.xml", now);
    addSitemapEntry(xml, "/sitemaps/categories.xml", now);
    addSitemapEntry(xml, "/sitemap-news.xml", now);

    long totalPosts = blogPostRepository.countByStatus(PostStatus.PUBLISHED);
    int totalPages = (int) Math.ceil((double) totalPosts / POSTS_PER_SITEMAP);
    if (totalPages == 0) totalPages = 1;

    for (int i = 0; i < totalPages; i++) {
      addSitemapEntry(xml, "/sitemaps/posts-" + i + ".xml", now);
    }

    xml.append("</sitemapindex>");
    return xml.toString();
  }

  @Cacheable(value = "sitemap_news", key = "'news_48h'")
  @Transactional(readOnly = true)
  public String generateNewsSitemap() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
    xml.append("        xmlns:news=\"http://www.google.com/schemas/sitemap-news/0.9\"\n");
    xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");

    Instant fortyEightHoursAgo = Instant.now().minus(48, ChronoUnit.HOURS);
    List<BlogPost> newsPosts =
        blogPostRepository.findByStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            PostStatus.PUBLISHED, fortyEightHoursAgo);

    for (BlogPost post : newsPosts) {
      xml.append("  <url>\n");
      xml.append("    <loc>").append(buildPostUrl(post)).append("</loc>\n");
      xml.append("    <news:news>\n");
      xml.append("      <news:publication>\n");
      xml.append("        <news:name>Treishvaam Finance</news:name>\n");
      xml.append("        <news:language>en</news:language>\n");
      xml.append("      </news:publication>\n");
      xml.append("      <news:publication_date>")
          .append(formatDate(post.getCreatedAt()))
          .append("</news:publication_date>\n");
      xml.append("      <news:title>").append(escapeXml(post.getTitle())).append("</news:title>\n");
      if (post.getKeywords() != null) {
        xml.append("      <news:keywords>")
            .append(escapeXml(post.getKeywords()))
            .append("</news:keywords>\n");
      }
      xml.append("    </news:news>\n");
      if (post.getCoverImageUrl() != null) {
        String imgUrl = baseUrl + "/api/uploads/" + post.getCoverImageUrl();
        xml.append("    <image:image>\n");
        xml.append("      <image:loc>").append(imgUrl).append("</image:loc>\n");
        xml.append("    </image:image>\n");
      }
      xml.append("  </url>\n");
    }
    xml.append("</urlset>");
    return xml.toString();
  }

  @Cacheable(value = "sitemap_archive", key = "'page_' + #page")
  @Transactional(readOnly = true)
  public String generatePostsSitemap(int page) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
    xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");

    PageRequest pageRequest =
        PageRequest.of(page, POSTS_PER_SITEMAP, Sort.by("createdAt").descending());
    Page<BlogPost> postPage = blogPostRepository.findAllByStatus(PostStatus.PUBLISHED, pageRequest);

    for (BlogPost post : postPage) {
      xml.append("  <url>\n");
      xml.append("    <loc>").append(buildPostUrl(post)).append("</loc>\n");
      xml.append("    <lastmod>").append(formatDate(post.getUpdatedAt())).append("</lastmod>\n");
      if (post.getCoverImageUrl() != null) {
        String imgUrl = baseUrl + "/api/uploads/" + post.getCoverImageUrl();
        xml.append("    <image:image>\n");
        xml.append("      <image:loc>").append(imgUrl).append("</image:loc>\n");
        xml.append("    </image:image>\n");
      }
      xml.append("  </url>\n");
    }
    xml.append("</urlset>");
    return xml.toString();
  }

  @Cacheable(value = "sitemap_static", key = "'static_pages'")
  public String generateStaticSitemap() {
    String[] pages = {"/", "/about", "/vision", "/contact"};
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    String now = formatDate(Instant.now());
    for (String p : pages) {
      xml.append("  <url><loc>")
          .append(baseUrl)
          .append(p)
          .append("</loc><lastmod>")
          .append(now)
          .append("</lastmod></url>\n");
    }
    xml.append("</urlset>");
    return xml.toString();
  }

  @Cacheable(value = "sitemap_categories", key = "'categories'")
  public String generateCategoriesSitemap() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    List<Category> categories = categoryRepository.findAll();
    String now = formatDate(Instant.now());
    for (Category cat : categories) {
      if (cat.getSlug() == null) continue;
      String url = baseUrl + "/category/" + cat.getSlug();
      xml.append("  <url><loc>")
          .append(url)
          .append("</loc><lastmod>")
          .append(now)
          .append("</lastmod></url>\n");
    }
    xml.append("</urlset>");
    return xml.toString();
  }

  // --- NEW ENTERPRISE METHOD: CACHE EVICTION ---

  /**
   * Called via RabbitMQ (MessageListener) when a post is created/updated/deleted. This clears all
   * sitemap caches so the next visit to /sitemap.xml triggers a fresh DB query.
   */
  @CacheEvict(
      value = {
        "sitemap_index",
        "sitemap_news",
        "sitemap_archive",
        "sitemap_static",
        "sitemap_categories"
      },
      allEntries = true)
  public void clearCaches() {
    logger.info("Evicting all Sitemap Caches due to content update");
  }

  // --- Helpers ---
  private void addSitemapEntry(StringBuilder xml, String path, String lastMod) {
    xml.append("  <sitemap>\n");
    xml.append("    <loc>").append(baseUrl).append(path).append("</loc>\n");
    xml.append("    <lastmod>").append(lastMod).append("</lastmod>\n");
    xml.append("  </sitemap>\n");
  }

  private String buildPostUrl(BlogPost post) {
    String catSlug = (post.getCategory() != null) ? post.getCategory().getSlug() : "uncategorized";
    return String.format(
        "%s/category/%s/%s/%s",
        baseUrl, catSlug, post.getUserFriendlySlug(), post.getUrlArticleId());
  }

  private String formatDate(Instant instant) {
    if (instant == null) instant = Instant.now();
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC")).format(instant);
  }

  private String escapeXml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
