package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.SitemapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Public API endpoints for retrieving Sitemap XML chunks. - Consumed by Cloudflare
 * Worker for aggregation.
 *
 * <p>Scope: - /api/public/sitemap/**
 *
 * <p>Critical Dependencies: - SitemapService: Contains the generation logic.
 *
 * <p>Security Constraints: - Must be openly accessible (no Auth required). - Read-only.
 *
 * <p>Non-Negotiables: - Content-Type must be application/xml.
 *
 * <p>IMMUTABLE CHANGE HISTORY: - EDITED: • Added endpoints for Dynamic Index, Blog Parts, and
 * Market Parts. • Phase 2 - Hybrid Architecture.
 */
@RestController
@RequestMapping("/api/public/sitemap")
@RequiredArgsConstructor
@Tag(name = "Sitemap", description = "Endpoints for SEO Sitemap generation")
public class SitemapController {

  private final SitemapService sitemapService;

  @Operation(summary = "Get the Dynamic Sitemap Index")
  @GetMapping(value = "/index", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getDynamicIndex() {
    return ResponseEntity.ok(sitemapService.generateDynamicIndex());
  }

  @Operation(summary = "Get a segment of Blog URLs")
  @GetMapping(value = "/blog/{page}.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getBlogSitemap(@PathVariable int page) {
    return ResponseEntity.ok(sitemapService.generateBlogSitemap(page));
  }

  @Operation(summary = "Get a segment of Market Data URLs")
  @GetMapping(value = "/market/{page}.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getMarketSitemap(@PathVariable int page) {
    return ResponseEntity.ok(sitemapService.generateMarketSitemap(page));
  }
}
