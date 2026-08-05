/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - REST Controller for executing secured administrative actions.
 *
 * <p>Scope: - Handles manual triggers for system-wide events (e.g., sitemap regeneration,
 * historical data healing).
 *
 * <p>Critical Dependencies: - Backend: SitemapService, AnalyticsService, AegisZkpServiceClient.
 *
 * <p>Security Constraints: - Class-level @PreAuthorize strictly limits access to ROLE_ADMIN. -
 * Destructive or highly sensitive operations MUST be additionally gated by X-AEGIS-ZKP
 * zero-knowledge proofs.
 *
 * <p>Non-Negotiables: - Do not bypass ZKP validation for the analytics healer.
 *
 * <p>Change Intent: - Add ZKP-gated endpoint for the retroactive analytics data healer.
 *
 * <p>Future AI Guidance: - Append new admin actions here. Ensure ZKP is used for anything modifying
 * historical or immutable data.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Generated AI-CONTEXT block. • Implemented
 * ZKP-gated /analytics/heal endpoint to trigger Memory-Safe Retroactive Fidelity Restoration. •
 * 2026-08-05 / Incident 41
 *
 * <p>- EDITED (Incident 43 - Compilation Fix): • Fixed `verifyProof` method signature to pass 3
 * arguments (`adminId`, `challengeId`, `proofDataHex`) to satisfy the AegisZkpServiceClient gRPC
 * contract. • Date: 2026-08-05
 *
 * <p>- STRATEGIC PIVOTS & FAILED ATTEMPTS (CRITICAL FOR FUTURE AI): • Strategy Attempted: N/A •
 * Failure Mode: N/A • Date/Phase: 2026-08-05 • Future AI Warning: N/A
 */
package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.analytics.AnalyticsService;
import com.treishvaam.financeapi.security.aegis.AegisZkpServiceClient;
import com.treishvaam.financeapi.service.SitemapService; // FIXED IMPORT
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/actions")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminActionsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminActionsController.class);

    // FIXED: Injected SitemapService instead of GenerationService
    @Autowired private SitemapService sitemapService;

    @Autowired private AnalyticsService analyticsService;

    @Autowired private AegisZkpServiceClient aegisZkpService;

    @PostMapping("/regenerate-sitemap")
    public ResponseEntity<?> regenerateSitemap() {
        logger.info("Admin manually triggered sitemap cache flush.");
        try {
            // ENTERPRISE FIX: We clear the cache to force a fresh DB pull on next visit
            sitemapService.clearCaches();
            logger.info("Sitemap cache cleared successfully.");
            return ResponseEntity.ok(
                    Map.of("message", "Sitemap cache cleared. Fresh data is live."));
        } catch (Exception e) {
            logger.error("Admin-triggered sitemap cache clear failed", e);
            return ResponseEntity.status(500)
                    .body(Map.of("message", "Sitemap cache clear failed: " + e.getMessage()));
        }
    }

    @PostMapping("/analytics/heal")
    public ResponseEntity<?> healHistoricalAnalytics(
            @RequestHeader(value = "X-AEGIS-ZKP", required = true) String zkpProof) {
        // AEGIS Zero-Knowledge Proof validation
        // FIXED (Incident 43): Pass 3 arguments to satisfy the gRPC client signature
        if (!aegisZkpService.verifyProof("system_admin", "ANALYTICS_HEAL_ACTION", zkpProof)) {
            logger.warn("[AEGIS] ZKP Validation Failed for Data Healer Trigger.");
            return ResponseEntity.status(403)
                    .body(Map.of("error", "ZKP Verification Failed. Zero-Trust lock active."));
        }

        logger.info(
                "Admin successfully authenticated via ZKP. Triggering Retroactive Data Healer.");
        analyticsService.healHistoricalDataFidelity();
        return ResponseEntity.ok(
                Map.of("message", "Memory-safe historical data healing initiated in background."));
    }
}
