package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.MarketData;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.MarketDataRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
 * <p>Purpose: - Generates segmented, scalable XML sitemaps for dynamic content (10M+ records). -
 * Serves as the source of truth for "sitemap-dynamic" parts.
 *
 * <p>Scope: - Responsible for: Blogs, Market Data. - EXCLUDED: News (NewsHighlight) - as they
 * redirect to external/internal sources. - EXCLUDED: Static pages (handled by Frontend
 * sitemap-static.xml).
 *
 * <p>Critical Dependencies: - Backend: BlogPostRepository, MarketDataRepository. - Worker: Consumed
 * by Cloudflare Worker via /api/public/sitemap endpoints.
 *
 * <p>Security Constraints: - Publicly accessible data only. No internal dashboards or auth-gated
 * routes.
 *
 * <p>Non-Negotiables: - Must use pagination (batch size 50,000) to respect Google Sitemap limits. -
 * Must never block the main thread (use efficient db paging). - News must remain excluded to
 * prevent Soft 404s.
 *
 * <p>Change Intent: - Implementing Enterprise Hybrid Sitemap logic.
 *
 * <p>Future AI Guidance: - If adding new dynamic entities (e.g. Products), add a new segment type.
 * - Do not merge this back into a single monolithic XML file.
 *
 * <p>IMMUTABLE CHANGE HISTORY: - EDITED: • Implemented segmented sitemap generation (Market, Blog).
 * • Added pagination logic for 10M+ scale. • Excluded NewsHighlight explicitly. • Phase 2 - Hybrid
 * Architecture. - EDITED: • Added getSitemapMetadata() to support Flat Indexing in Worker. •
 * Replaced nested index generation with JSON metadata exposure. • Phase 3 - Flattening & Offline
 * Survival.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SitemapService {

  private final BlogPostRepository blogPostRepository;
  private final MarketDataRepository marketDataRepository;

  private static final String BASE_URL = "https://treishfin.treishvaamgroup.com";
  private static final int SITEMAP_BATCH_SIZE = 50000; // Google's limit per file

  /**
   * Returns a JSON-friendly map of all available sitemap files. Consumed by Cloudflare Worker to
   * build the Master Index.
   */
  public Map<String, List<String>> getSitemapMetadata() {
    Map<String, List<String>> meta = new HashMap<>();

    // 1. Calculate Blog Files
    long totalBlogs = blogPostRepository.countByStatus(PostStatus.PUBLISHED);
    int blogPages = (int) Math.ceil((double) totalBlogs / SITEMAP_BATCH_SIZE);
    if (blogPages == 0) blogPages = 1;

    List<String> blogFiles = new ArrayList<>();
    for (int i = 0; i < blogPages; i++) {
      blogFiles.add("/sitemap-dynamic/blog/" + i + ".xml");
    }
    meta.put("blogs", blogFiles);

    // 2. Calculate Market Files
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
    Page<BlogPost> posts = blogPostRepository.findByStatus(PostStatus.PUBLISHED, pageable);

    return buildUrlSet(
        posts.getContent().stream()
            .map(
                post -> {
                  String slug = post.getSlug() != null ? post.getSlug() : post.getId().toString();
                  String date =
                      post.getUpdatedAt() != null
                          ? post.getUpdatedAt().format(DateTimeFormatter.ISO_DATE)
                          : post.getCreatedAt().format(DateTimeFormatter.ISO_DATE);
                  return new SitemapEntry(BASE_URL + "/post/" + slug, date, "weekly", "0.8");
                })
            .collect(Collectors.toList()));
  }

  public String generateMarketSitemap(int page) {
    Pageable pageable = PageRequest.of(page, SITEMAP_BATCH_SIZE);
    Page<MarketData> data = marketDataRepository.findAll(pageable);

    return buildUrlSet(
        data.getContent().stream()
            .map(
                market -> {
                  String slug = market.getSymbol();
                  return new SitemapEntry(BASE_URL + "/market/" + slug, null, "daily", "0.6");
                })
            .collect(Collectors.toList()));
  }

  private String buildUrlSet(java.util.List<SitemapEntry> entries) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    for (SitemapEntry entry : entries) {
      xml.append("  <url>\n");
      xml.append("    <loc>").append(entry.loc).append("</loc>\n");
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
  }
}
