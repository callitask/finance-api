package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.marketdata.MarketData;
import com.treishvaam.financeapi.marketdata.MarketDataRepository;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
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

/** AI-CONTEXT: Purpose: Generates segmented sitemaps and metadata for Cloudflare Worker. */
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
        posts.getContent().stream()
            .map(
                post -> {
                  String slug = post.getSlug() != null ? post.getSlug() : post.getId().toString();
                  String date =
                      post.getUpdatedAt() != null
                          ? post.getUpdatedAt().toString()
                          : post.getCreatedAt().toString();
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
                  String slug = market.getTicker();
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
