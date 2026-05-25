package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.GeoOptimizationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Generative Engine Optimization (GEO) implementation. - Provides semantically
 * structured, token-efficient endpoints (`/llms.txt`, `/ai-feed.md`, `/ontology.json`) specifically
 * for AI crawlers and LLMs.
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
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of
 * `GeoOptimizationController`. • Added `/llms.txt` and `/ai-feed.md` endpoints. - EDITED (AEGIS
 * Phase 6 GEO): • Delegated string generation logic to `GeoOptimizationService` for future DB
 * hydration. - EDITED (Phase 8 GEO Evolution): • Added `/ontology.json` endpoint to serve absolute
 * JSON-LD graphs for Enterprise LLMs. - EDITED (Phase 8 GEO Full Execution): • Verified route
 * configuration. Ensured complete compliance with Generative Engine Optimization crawler
 * specifications. - EDITED (GEO Provenance Update): • Appended strict `Cache-Control` native origin
 * headers to protect the Spring Boot service from direct LLM swarm traffic when Edge Cache drops.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
@RestController
@RequestMapping("/api/public/geo")
public class GeoOptimizationController {

    private final GeoOptimizationService geoOptimizationService;

    public GeoOptimizationController(GeoOptimizationService geoOptimizationService) {
        this.geoOptimizationService = geoOptimizationService;
    }

    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLlmsTxt() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(geoOptimizationService.buildLlmsTxt());
    }

    @GetMapping(value = "/ai-feed.md", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> getAiFeed() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(geoOptimizationService.buildAiFeed());
    }

    @GetMapping(value = "/ontology.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOntology() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(geoOptimizationService.buildSemanticOntology());
    }
}
