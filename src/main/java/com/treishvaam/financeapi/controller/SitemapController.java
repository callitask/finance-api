package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.SitemapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Enterprise SEO Controller Serves dynamic sitemaps with high-performance caching. */
@RestController
public class SitemapController {

  @Autowired private SitemapService sitemapService;

  // 1. The Master Index
  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getSitemapIndex() {
    return ResponseEntity.ok(sitemapService.generateSitemapIndex());
  }

  // 2. Google News (Last 48h)
  @GetMapping(value = "/sitemap-news.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getNewsSitemap() {
    return ResponseEntity.ok(sitemapService.generateNewsSitemap());
  }

  // 3. Static Pages
  @GetMapping(value = "/sitemaps/static.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getStaticSitemap() {
    return ResponseEntity.ok(sitemapService.generateStaticSitemap());
  }

  // 4. Categories
  @GetMapping(value = "/sitemaps/categories.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getCategoriesSitemap() {
    return ResponseEntity.ok(sitemapService.generateCategoriesSitemap());
  }

  // 5. The Archives (Sharded)
  // Matches /sitemaps/posts-0.xml, /sitemaps/posts-1.xml, etc.
  @GetMapping(value = "/sitemaps/posts-{page}.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getPostsSitemap(@PathVariable int page) {
    return ResponseEntity.ok(sitemapService.generatePostsSitemap(page));
  }
}
