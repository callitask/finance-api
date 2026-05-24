/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Generative Engine Optimization (GEO) implementation. - Provides semantically
 * structured, token-efficient endpoints (`/llms.txt`, `/ai-feed.md`) specifically for AI crawlers
 * and LLMs.
 *
 * <p>Scope: - Responsible for aggregating public market data, company vision, and public news into
 * AI-readable formats. - Must NEVER output PII, internal user data, or protected draft states (GDPR
 * Compliance Lock).
 *
 * <p>Critical Dependencies: - SEO/Edge: Whitelisted by Cloudflare worker to allow GPTBot/ClaudeBot
 * native ingestion.
 *
 * <p>Security Constraints: - Output must remain strictly public (100% GDPR compliant).
 *
 * <p>Non-Negotiables: - Must return raw `text/plain` or `text/markdown` to ensure optimal LLM
 * tokenization.
 *
 * <p>Change Intent: - Added to establish a strong digital footprint in generative AI search spaces
 * (SearchGPT, Perplexity, Claude).
 *
 * <p>Future AI Guidance: - When adding new public data models, map them to this controller for
 * automatic AI ingestion.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of
 * `GeoOptimizationController`. • Added `/llms.txt` and `/ai-feed.md` endpoints. - EDITED (AEGIS
 * Phase 6 GEO): • Delegated string generation logic to `GeoOptimizationService` for future DB
 * hydration.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.GeoOptimizationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/geo")
public class GeoOptimizationController {

    private final GeoOptimizationService geoOptimizationService;

    public GeoOptimizationController(GeoOptimizationService geoOptimizationService) {
        this.geoOptimizationService = geoOptimizationService;
    }

    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLlmsTxt() {
        return ResponseEntity.ok(geoOptimizationService.buildLlmsTxt());
    }

    @GetMapping(value = "/ai-feed.md", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> getAiFeed() {
        return ResponseEntity.ok(geoOptimizationService.buildAiFeed());
    }
}
