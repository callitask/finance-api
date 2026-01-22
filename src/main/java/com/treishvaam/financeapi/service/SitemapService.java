package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.MarketData;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.MarketDataRepository;
import java.time.format.DateTimeFormatter;
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
 * Architecture.
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
   * Generates the Dynamic Index pointing to all child sitemaps.
   *
   * @return XML string <sitemapindex>
   */
  public String generateDynamicIndex() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    // 1. Calculate Blog Pages
    long totalBlogs = blogPostRepository.countByStatus(PostStatus.PUBLISHED);
    int blogPages = (int) Math.ceil((double) totalBlogs / SITEMAP_BATCH_SIZE);
    if (blogPages == 0) blogPages = 1; // Ensure at least one exists even if empty

    for (int i = 0; i < blogPages; i++) {
      xml.append("  <sitemap>\n");
      xml.append("    <loc>")
          .append(BASE_URL)
          .append("/sitemap-dynamic/blog/")
          .append(i)
          .append(".xml</loc>\n");
      xml.append("  </sitemap>\n");
    }

    // 2. Calculate Market Pages
    long totalMarket = marketDataRepository.count();
    int marketPages = (int) Math.ceil((double) totalMarket / SITEMAP_BATCH_SIZE);
    if (marketPages == 0) marketPages = 1;

    for (int i = 0; i < marketPages; i++) {
      xml.append("  <sitemap>\n");
      xml.append("    <loc>")
          .append(BASE_URL)
          .append("/sitemap-dynamic/market/")
          .append(i)
          .append(".xml</loc>\n");
      xml.append("  </sitemap>\n");
    }

    xml.append("</sitemapindex>");
    return xml.toString();
  }

  public String generateBlogSitemap(int page) {
    Pageable pageable = PageRequest.of(page, SITEMAP_BATCH_SIZE);
    Page<BlogPost> posts = blogPostRepository.findByStatus(PostStatus.PUBLISHED, pageable);

    return buildUrlSet(
        posts.getContent().stream()
            .map(
                post -> {
                  String slug = post.getSlug() != null ? post.getSlug() : post.getId().toString();
                  // Assuming getLastModifiedAt() exists, otherwise use getCreatedAt()
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
                  // Market Symbol as slug
                  String slug = market.getSymbol();
                  // Market data changes frequently
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
