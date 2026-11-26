package com.treishvaam.financeapi.controller;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Admin controller for managing the CorporateEntity graph (GEO entity context and
 * admin-managed entity payloads).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (P2.4 - Entity Upsert Validation, VULN-13a):
 * • Injected `ObjectMapper` via constructor. • `upsert()`: validates `entityKey` matches
 * `^[a-z0-9_]{2,80}$` (400 on mismatch). • Validates `jsonLdPayload` via
 * `objectMapper.readTree(payload)` requiring `parsed.isObject() && parsed.hasNonNull("@type") &&
 * parsed.hasNonNull("@id")` (400 on missing/malformed — payload required on create).
 *
 * <p>- EDITED (Batch 3B - P3.4, VULN-13b): • Injected `GeoPromptSanitizer` via constructor. • On
 * both create (`upsert()`) and update paths, `llmsTxtBlock` and `aiFeedChunk` are sanitized via
 * `geoPromptSanitizer.sanitizeEntityBlock(...)` before persisting and HMAC-signing (OWASP
 * LLM01:2026).
 *
 * <p>- EDITED (2026-11-26 - Batch 2 §2.4 GEO Entity JSON-LD Cap, Sanitization & Full-Node Signing):
 * • Enforced `MAX_JSON_LD_BYTES = 32_768` cap (400 Bad Request if exceeded). • Applied
 * `geoPromptSanitizer.sanitizeJsonLdNode(parsed)` before persisting `jsonLdPayload`. • Signed full
 * entity payload via `GeoOptimizationService.buildEntitySigningPayload(...)` on both create and
 * update.
 */
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.model.CorporateEntityNode;
import com.treishvaam.financeapi.security.ContentIntegrityService;
import com.treishvaam.financeapi.security.GeoPromptSanitizer;
import com.treishvaam.financeapi.service.GeoOptimizationService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/entities")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class CorporateEntityAdminController {

    private static final Logger logger =
            LoggerFactory.getLogger(CorporateEntityAdminController.class);
    private static final int MAX_JSON_LD_BYTES = 32_768;

    private final com.treishvaam.financeapi.repository.CorporateEntityNodeRepository repository;
    private final ContentIntegrityService contentIntegrityService;
    private final GeoOptimizationService geoOptimizationService;
    private final ObjectMapper objectMapper;
    private final GeoPromptSanitizer geoPromptSanitizer;

    public CorporateEntityAdminController(
            com.treishvaam.financeapi.repository.CorporateEntityNodeRepository repository,
            ContentIntegrityService contentIntegrityService,
            GeoOptimizationService geoOptimizationService,
            ObjectMapper objectMapper,
            GeoPromptSanitizer geoPromptSanitizer) {
        this.repository = repository;
        this.contentIntegrityService = contentIntegrityService;
        this.geoOptimizationService = geoOptimizationService;
        this.objectMapper = objectMapper;
        this.geoPromptSanitizer = geoPromptSanitizer;
    }

    @GetMapping
    public ResponseEntity<List<CorporateEntityNode>> listAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PutMapping("/{entityKey}")
    public ResponseEntity<CorporateEntityNode> upsert(
            @PathVariable String entityKey, @RequestBody CorporateEntityNode incoming) {
        if (entityKey == null || !entityKey.matches("^[a-z0-9_]{2,80}$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String payload = incoming.getJsonLdPayload();
        CorporateEntityNode existing = repository.findByEntityKey(entityKey).orElse(null);
        if (existing == null && payload == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String sanitizedJsonLd = null;
        if (payload != null) {
            if (payload.length() > MAX_JSON_LD_BYTES
                    || payload.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_LD_BYTES) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            try {
                JsonNode parsed = objectMapper.readTree(payload);
                if (!parsed.isObject()
                        || !parsed.hasNonNull("@type")
                        || !parsed.hasNonNull("@id")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
                geoPromptSanitizer.sanitizeJsonLdNode(parsed);
                sanitizedJsonLd = objectMapper.writeValueAsString(parsed);
            } catch (JsonProcessingException e) {
                logger.warn("Invalid jsonLdPayload for entity '{}': malformed JSON", entityKey);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }
        if (existing == null) {
            incoming.setEntityKey(entityKey);
            if (incoming.getDisplayOrder() == null) {
                incoming.setDisplayOrder(0);
            }
            if (incoming.getActive() == null) {
                incoming.setActive(Boolean.TRUE);
            }
            if (incoming.getTenantId() == null) {
                incoming.setTenantId("global");
            }
            // P3.4 (VULN-13b): Sanitize JSON-LD & entity blocks before persisting + HMAC-signing
            incoming.setJsonLdPayload(sanitizedJsonLd);
            incoming.setLlmsTxtBlock(
                    geoPromptSanitizer.sanitizeEntityBlock(incoming.getLlmsTxtBlock()));
            incoming.setAiFeedChunk(
                    geoPromptSanitizer.sanitizeEntityBlock(incoming.getAiFeedChunk()));
            String signature =
                    contentIntegrityService.computeSignature(
                            GeoOptimizationService.buildEntitySigningPayload(incoming));
            incoming.setContentSignature(signature);
            CorporateEntityNode saved = repository.save(incoming);
            geoOptimizationService.evictGeoCache();
            logger.info("Corporate entity upserted: {}", entityKey);
            return ResponseEntity.ok(saved);
        }
        existing.setEntityType(
                incoming.getEntityType() != null
                        ? incoming.getEntityType()
                        : existing.getEntityType());
        existing.setCanonicalId(
                incoming.getCanonicalId() != null
                        ? incoming.getCanonicalId()
                        : existing.getCanonicalId());
        existing.setName(incoming.getName() != null ? incoming.getName() : existing.getName());
        existing.setLegalName(
                incoming.getLegalName() != null
                        ? incoming.getLegalName()
                        : existing.getLegalName());
        existing.setRoleOrSubtitle(
                incoming.getRoleOrSubtitle() != null
                        ? incoming.getRoleOrSubtitle()
                        : existing.getRoleOrSubtitle());
        existing.setUrl(incoming.getUrl() != null ? incoming.getUrl() : existing.getUrl());
        existing.setImageUrl(
                incoming.getImageUrl() != null ? incoming.getImageUrl() : existing.getImageUrl());
        existing.setDescription(
                incoming.getDescription() != null
                        ? incoming.getDescription()
                        : existing.getDescription());
        existing.setJsonLdPayload(
                sanitizedJsonLd != null ? sanitizedJsonLd : existing.getJsonLdPayload());
        existing.setLlmsTxtBlock(
                incoming.getLlmsTxtBlock() != null
                        ? geoPromptSanitizer.sanitizeEntityBlock(incoming.getLlmsTxtBlock())
                        : existing.getLlmsTxtBlock());
        existing.setAiFeedChunk(
                incoming.getAiFeedChunk() != null
                        ? geoPromptSanitizer.sanitizeEntityBlock(incoming.getAiFeedChunk())
                        : existing.getAiFeedChunk());
        existing.setDisplayOrder(
                incoming.getDisplayOrder() != null
                        ? incoming.getDisplayOrder()
                        : existing.getDisplayOrder());
        existing.setActive(
                incoming.getActive() != null ? incoming.getActive() : existing.getActive());
        String signature =
                contentIntegrityService.computeSignature(
                        GeoOptimizationService.buildEntitySigningPayload(existing));
        existing.setContentSignature(signature);
        CorporateEntityNode saved = repository.save(existing);
        geoOptimizationService.evictGeoCache();
        logger.info("Corporate entity updated: {}", entityKey);
        return ResponseEntity.ok(saved);
    }
}
