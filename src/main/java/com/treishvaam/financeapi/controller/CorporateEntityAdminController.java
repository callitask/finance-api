package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.CorporateEntityNode;
import com.treishvaam.financeapi.security.ContentIntegrityService;
import com.treishvaam.financeapi.service.GeoOptimizationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final com.treishvaam.financeapi.repository.CorporateEntityNodeRepository repository;
    private final ContentIntegrityService contentIntegrityService;
    private final GeoOptimizationService geoOptimizationService;

    public CorporateEntityAdminController(
            com.treishvaam.financeapi.repository.CorporateEntityNodeRepository repository,
            ContentIntegrityService contentIntegrityService,
            GeoOptimizationService geoOptimizationService) {
        this.repository = repository;
        this.contentIntegrityService = contentIntegrityService;
        this.geoOptimizationService = geoOptimizationService;
    }

    @GetMapping
    public ResponseEntity<List<CorporateEntityNode>> listAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PutMapping("/{entityKey}")
    public ResponseEntity<CorporateEntityNode> upsert(
            @PathVariable String entityKey, @RequestBody CorporateEntityNode incoming) {
        CorporateEntityNode existing = repository.findByEntityKey(entityKey).orElse(null);
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
            String signature =
                    contentIntegrityService.computeSignature(incoming.getJsonLdPayload());
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
                incoming.getJsonLdPayload() != null
                        ? incoming.getJsonLdPayload()
                        : existing.getJsonLdPayload());
        existing.setLlmsTxtBlock(
                incoming.getLlmsTxtBlock() != null
                        ? incoming.getLlmsTxtBlock()
                        : existing.getLlmsTxtBlock());
        existing.setAiFeedChunk(
                incoming.getAiFeedChunk() != null
                        ? incoming.getAiFeedChunk()
                        : existing.getAiFeedChunk());
        existing.setDisplayOrder(
                incoming.getDisplayOrder() != null
                        ? incoming.getDisplayOrder()
                        : existing.getDisplayOrder());
        existing.setActive(
                incoming.getActive() != null ? incoming.getActive() : existing.getActive());
        String signature = contentIntegrityService.computeSignature(existing.getJsonLdPayload());
        existing.setContentSignature(signature);
        CorporateEntityNode saved = repository.save(existing);
        geoOptimizationService.evictGeoCache();
        logger.info("Corporate entity updated: {}", entityKey);
        return ResponseEntity.ok(saved);
    }
}
