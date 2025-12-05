package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.SitemapGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/actions")
@PreAuthorize("hasAuthority('ROLE_ADMIN')") 
public class AdminActionsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminActionsController.class);

    @Autowired
    private SitemapGenerationService sitemapGenerationService;

    @PostMapping("/regenerate-sitemap")
    public ResponseEntity<?> regenerateSitemap() {
        logger.info("Admin manually triggered sitemap regeneration.");
        try {
            sitemapGenerationService.generateSitemaps();
            logger.info("Sitemap regeneration completed successfully.");
            return ResponseEntity.ok(Map.of("message", "Sitemap regeneration complete."));
        } catch (Exception e) {
            logger.error("Admin-triggered sitemap regeneration failed", e);
            return ResponseEntity.status(500).body(Map.of("message", "Sitemap generation failed: " + e.getMessage()));
        }
    }
}