package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.config.tenant.TenantContext;
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
import org.springframework.beans.factory.annotation.Value;
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
 * XML sitemap validation errors in Google Search Console. - EDITED: • Phase 3 (Backend Dynamic
 * Integration): Added multi-tenant sitemap logic. • Dynamic BASE_URL resolution via TenantContext.
 * • Agro tenant seamlessly hijacks the blog/market endpoint structure to deliver static E-E-A-T
 * pages. - EDITED (GEO OpenSearch Phase): • Injected `/llms.txt`, `/ai-feed.md`, and
 * `/ontology.json` into the root sitemap output (page 0 and Agro root) with priority 1.0. • Why:
 * Ensures AI agents automatically discover Generative Engine Optimization payloads during routine
 * sitemap crawls without requiring manual search console submission. - EDITED (Post-Approval -
 * Enterprise Sitemap Optimization): • Replaced hardcoded legacy domains with Spring @Value
 * properties (`app.finance.domain`) to future-proof against domain migrations and fix GSC
 * cross-domain penalties. • Reduced `SITEMAP_BATCH_SIZE` from 50,000 to 10,000 to parallelize
 * Googlebot ingestion and completely eliminate 503 TTFB timeouts. - DO-NOT-DELETE RULE (ABSOLUTE):
 * This IMMUTABLE CHANGE HISTORY section acts as the institutional memory for future AI sessions. It
 * must never be deleted, truncated, rewritten, or regenerated. Future AI must append only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SitemapService {

    private final BlogPostRepository blogPostRepository;
    private final MarketDataRepository marketDataRepository;

    @Value("${app.finance.domain:https://treishvaamfinance.com}")
    private String financeDomain;

    @Value("${app.agro.domain:https://treishvaamagro.com}")
    private String agroDomain;

    private static final int SITEMAP_BATCH_SIZE = 10000;

    private String getBaseUrl() {
        return "agro".equals(TenantContext.getTenantId()) ? agroDomain : financeDomain;
    }

    /** Clears internal caches. Kept for dependency compatibility. */
    public void clearCaches() {
        log.info("Sitemap clearCaches invoked. Edge handling active.");
    }

    /** Returns a JSON-friendly map of all available sitemap files. */
    public Map<String, List<String>> getSitemapMetadata() {
        String tenantId = TenantContext.getTenantId();
        Map<String, List<String>> meta = new HashMap<>();

        // AGRO TENANT OVERRIDE
        if ("agro".equals(tenantId)) {
            meta.put(
                    "pages",
                    List.of("/sitemap-dynamic/blog/0.xml")); // Repurpose endpoint for static pages
            return meta;
        }

        // 1. Blogs (Finance)
        long totalBlogs = blogPostRepository.countByStatus(PostStatus.PUBLISHED);
        int blogPages = (int) Math.ceil((double) totalBlogs / SITEMAP_BATCH_SIZE);
        if (blogPages == 0) blogPages = 1;

        List<String> blogFiles = new ArrayList<>();
        for (int i = 0; i < blogPages; i++) {
            blogFiles.add("/sitemap-dynamic/blog/" + i + ".xml");
        }
        meta.put("blogs", blogFiles);

        // 2. Markets (Finance)
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
        String tenantId = TenantContext.getTenantId();

        // AGRO TENANT OVERRIDE (Serves static enterprise pages)
        if ("agro".equals(tenantId)) {
            return buildAgroStaticSitemap();
        }

        Pageable pageable = PageRequest.of(page, SITEMAP_BATCH_SIZE);
        Page<BlogPost> posts = blogPostRepository.findAllByStatus(PostStatus.PUBLISHED, pageable);

        List<SitemapEntry> entries = new ArrayList<>();

        // Inject GEO endpoints into the very first sitemap block (page 0) for AI Crawlers
        if (page == 0) {
            String baseUrl = getBaseUrl();
            entries.add(new SitemapEntry(baseUrl + "/llms.txt", null, "daily", "1.0"));
            entries.add(new SitemapEntry(baseUrl + "/ai-feed.md", null, "daily", "1.0"));
            entries.add(new SitemapEntry(baseUrl + "/ontology.json", null, "daily", "1.0"));
        }

        entries.addAll(
                posts.getContent().stream()
                        .map(this::constructBlogPostUrl)
                        .collect(Collectors.toList()));

        return buildUrlSet(entries);
    }

    private String buildAgroStaticSitemap() {
        String baseUrl = getBaseUrl();
        List<SitemapEntry> entries = new ArrayList<>();

        // GEO Endpoints for Agro
        entries.add(new SitemapEntry(baseUrl + "/llms.txt", null, "daily", "1.0"));
        entries.add(new SitemapEntry(baseUrl + "/ai-feed.md", null, "daily", "1.0"));
        entries.add(new SitemapEntry(baseUrl + "/ontology.json", null, "daily", "1.0"));

        entries.add(new SitemapEntry(baseUrl, null, "weekly", "1.0"));
        entries.add(new SitemapEntry(baseUrl + "/about", null, "monthly", "0.8"));
        entries.add(new SitemapEntry(baseUrl + "/infrastructure", null, "monthly", "0.8"));
        entries.add(new SitemapEntry(baseUrl + "/quality", null, "monthly", "0.8"));
        entries.add(new SitemapEntry(baseUrl + "/sustainability", null, "monthly", "0.8"));
        entries.add(new SitemapEntry(baseUrl + "/products", null, "monthly", "0.8"));
        entries.add(new SitemapEntry(baseUrl + "/contact", null, "monthly", "0.8"));
        return buildUrlSet(entries);
    }

    /** Helper to construct the exact Frontend Route URL */
    private SitemapEntry constructBlogPostUrl(BlogPost post) {
        Category cat = post.getCategory();
        String categorySlug = (cat != null && cat.getSlug() != null) ? cat.getSlug() : "general";
        String userSlug =
                post.getUserFriendlySlug() != null ? post.getUserFriendlySlug() : post.getSlug();
        String articleId =
                post.getUrlArticleId() != null
                        ? post.getUrlArticleId()
                        : (post.getSlug() != null ? post.getSlug() : String.valueOf(post.getId()));

        String loc =
                String.format(
                        "%s/category/%s/%s/%s", getBaseUrl(), categorySlug, userSlug, articleId);

        String date =
                post.getUpdatedAt() != null
                        ? post.getUpdatedAt().toString()
                        : post.getCreatedAt().toString();

        return new SitemapEntry(loc, date, "weekly", "0.8");
    }

    public String generateMarketSitemap(int page) {
        String tenantId = TenantContext.getTenantId();
        if ("agro".equals(tenantId)) {
            return buildUrlSet(new ArrayList<>()); // Agro has no market data
        }

        Pageable pageable = PageRequest.of(page, SITEMAP_BATCH_SIZE);
        Page<MarketData> data = marketDataRepository.findAll(pageable);

        LinkedHashSet<SitemapEntry> uniqueEntries =
                data.getContent().stream()
                        .map(
                                market -> {
                                    String slug = market.getTicker();
                                    return new SitemapEntry(
                                            getBaseUrl() + "/market/" + slug, null, "daily", "0.6");
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
