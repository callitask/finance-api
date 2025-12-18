package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.SitemapService; // FIXED IMPORT
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/actions")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminActionsController {

  private static final Logger logger = LoggerFactory.getLogger(AdminActionsController.class);

  // FIXED: Injected SitemapService instead of GenerationService
  @Autowired private SitemapService sitemapService;

  @PostMapping("/regenerate-sitemap")
  public ResponseEntity<?> regenerateSitemap() {
    logger.info("Admin manually triggered sitemap cache flush.");
    try {
      // ENTERPRISE FIX: We clear the cache to force a fresh DB pull on next visit
      sitemapService.clearCaches();
      logger.info("Sitemap cache cleared successfully.");
      return ResponseEntity.ok(Map.of("message", "Sitemap cache cleared. Fresh data is live."));
    } catch (Exception e) {
      logger.error("Admin-triggered sitemap cache clear failed", e);
      return ResponseEntity.status(500)
          .body(Map.of("message", "Sitemap cache clear failed: " + e.getMessage()));
    }
  }
}
