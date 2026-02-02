package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.marketdata.MarketData;
import com.treishvaam.financeapi.marketdata.MarketDataRepository;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Generates segmented sitemaps and metadata for Cloudflare Worker. - Provides
 * SEO-compliant XML sitemaps for Google Search Console.
 *
 * <p>Scope: - Blog post sitemap generation with category/slug routing - Market data sitemap
 * generation with ticker symbols - Metadata endpoint for Worker KV caching
 *
 * <p>Critical Dependencies: - Backend: BlogPostRepository, MarketDataRepository - Worker: Consumes
 * /api/public/sitemap/* endpoints - Frontend: Routing structure must match URL construction
 *
 * <p>Security Constraints: - Public endpoints (no auth required) - Read-only operations - No
 * sensitive data exposed
 *
 * <p>Non-Negotiables: - URLs must match frontend React Router paths exactly - No duplicate entries
 * (causes Google sitemap errors) - Proper XML escaping for special characters - lastmod timestamps
 * for change detection
 *
 * <p>IMMUTABLE CHANGE HISTORY: - UPDATED: URL Construction to match Frontend Router:
 * /category/{catSlug}/{userSlug}/{articleId} - ADDED: Null-safe fallbacks for slugs and categories.
 * - CRITICAL FIX (2026-02-02): Added duplicate removal in market sitemap generation. • Reason:
 * Google Search Console "Temporary processing error" due to duplicate ticker entries. • Solution:
 * Use LinkedHashSet to eliminate duplicates while preserving insertion order. • Impact: Prevents
 * XML sitemap validation errors in Google Search Console.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SitemapService {

  private final BlogPostRepository blogPostRepository;
  private final MarketDataRepository marketDataRepository;

  private static final String BASE_URL = "https://treishfin.treishvaamgroup.com";
  private static final int SITEMAP_BATCH_SIZE = 50000;

  /** Clears internal caches. Kept for dependency compatibility. */
  public void clearCaches() {
    log.info("Sitemap clearCaches invoked. Edge handling active.");
  }

  /** Returns a JSON-friendly map of all available sitemap files. */
  public Map<String, List<String>> getSitemapMetadata() {
    Map<String, List<String>> meta = new HashMap<>();

    // 1. Blogs
    long totalBlogs = blogPostRepository.countByStatus(PostStatus.PUBLISHED);
    int blogPages = (int) Math.ceil((double) totalBlogs / SITEMAP_BATCH_SIZE);
    if (blogPages == 0) blogPages = 1;

    List<String> blogFiles = new ArrayList<>();
    for (int i = 0; i < blogPages; i++) {
      blogFiles.add("/sitemap-dynamic/blog/" + i + ".xml");
    }
    meta.put("blogs", blogFiles);

    // 2. Markets
    long totalMarket = marketDataRepository.count();
    int marketPages = (int) Math.ceil((double) totalMarket / SITEMAP_BATCH_SIZE);
    if (marketPages == 0) marketPages = 1;

    List<String> marketFiles = new ArrayList<>();
    for (int i = 0; i < marketPages; i++) {
      marketFiles.add("/sitemap-dynamic/market/" + i + ".xml");
    }
    meta.put("markets", marketFiles);

    return meta;
  }

  public String generateBlogSitemap(int page) {
    Pageable pageable = PageRequest.of(page, SITEMAP_BATCH_SIZE);
    Page<BlogPost> posts = blogPostRepository.findAllByStatus(PostStatus.PUBLISHED, pageable);

    return buildUrlSet(
        posts.getContent().stream().map(this::constructBlogPostUrl).collect(Collectors.toList()));
  }

  /** Helper to construct the exact Frontend Route URL */
  private SitemapEntry constructBlogPostUrl(BlogPost post) {
    // Extract fields with safety checks
    Category cat = post.getCategory();
    String categorySlug = (cat != null && cat.getSlug() != null) ? cat.getSlug() : "general";
    String userSlug =
        post.getUserFriendlySlug() != null ? post.getUserFriendlySlug() : post.getSlug();
    String articleId =
        post.getUrlArticleId() != null
            ? post.getUrlArticleId()
            : (post.getSlug() != null ? post.getSlug() : String.valueOf(post.getId()));

    // Construct Format: /category/:categorySlug/:userFriendlySlug/:urlArticleId
    String loc = String.format("%s/category/%s/%s/%s", BASE_URL, categorySlug, userSlug, articleId);

    String date =
        post.getUpdatedAt() != null
            ? post.getUpdatedAt().toString()
            : post.getCreatedAt().toString();

    return new SitemapEntry(loc, date, "weekly", "0.8");
  }

  /**
   * CRITICAL FIX (2026-02-02): Duplicate Removal
   *
   * <p>Problem: MarketData table contains duplicate ticker entries causing Google sitemap
   * validation errors. Solution: Use LinkedHashSet to automatically deduplicate while preserving
   * insertion order.
   */
  public String generateMarketSitemap(int page) {
    Pageable pageable = PageRequest.of(page, SITEMAP_BATCH_SIZE);
    Page<MarketData> data = marketDataRepository.findAll(pageable);

    // Use LinkedHashSet to eliminate duplicates while preserving order
    LinkedHashSet<SitemapEntry> uniqueEntries =
        data.getContent().stream()
            .map(
                market -> {
                  String slug = market.getTicker();
                  return new SitemapEntry(BASE_URL + "/market/" + slug, null, "daily", "0.6");
                })
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return buildUrlSet(new ArrayList<>(uniqueEntries));
  }

  private String buildUrlSet(java.util.List<SitemapEntry> entries) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    for (SitemapEntry entry : entries) {
      xml.append("  <url>\n");
      xml.append("    <loc>").append(escapeXml(entry.loc)).append("</loc>\n");
      if (entry.lastmod != null) {
        xml.append("    <lastmod>").append(entry.lastmod).append("</lastmod>\n");
      }
      xml.append("    <changefreq>").append(entry.changefreq).append("</changefreq>\n");
      xml.append("    <priority>").append(entry.priority).append("</priority>\n");
      xml.append("  </url>\n");
    }

    xml.append("</urlset>");
    return xml.toString();
  }

  /** Escapes special XML characters to prevent malformed XML. */
  private String escapeXml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static class SitemapEntry {
    String loc;
    String lastmod;
    String changefreq;
    String priority;

    public SitemapEntry(String loc, String lastmod, String changefreq, String priority) {
      this.loc = loc;
      this.lastmod = lastmod;
      this.changefreq = changefreq;
      this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SitemapEntry that = (SitemapEntry) o;
      return loc.equals(that.loc);
    }

    @Override
    public int hashCode() {
      return loc.hashCode();
    }
  }
}
