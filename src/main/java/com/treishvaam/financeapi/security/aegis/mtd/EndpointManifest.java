/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Data structure representing the shifting API paths used in the Moving Target
 * Defense (MTD) architecture.
 *
 * <p>Scope: - Maps canonical standard endpoints (e.g., /api/v1/auth) to temporal polymorphic
 * endpoints.
 *
 * <p>Critical Dependencies: - Backend: AegisTemporalPathManager pushes this to Redis. - Edge:
 * Cloudflare Workers pull this to resolve routing.
 *
 * <p>Security Constraints: - Must include a cryptographic signature (ML-DSA) so the Edge can verify
 * it wasn't tampered with via Redis injection.
 *
 * <p>Non-Negotiables: - Always serialize to JSON cleanly.
 *
 * <p>Change Intent: - Fix-forward remediation: DTO for L2-PPO.
 *
 * <p>Future AI Guidance: - Do not rename fields without coordinating with the Cloudflare Worker
 * codebase.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • EndpointManifest DTO. • Phase 3
 * Remediation Batch.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis.mtd;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EndpointManifest {

    private long epochTimestamp;
    private Map<String, String> pathMappings;
    private String mlDsaSignature;

    public EndpointManifest() {
        this.pathMappings = new ConcurrentHashMap<>();
        this.epochTimestamp = System.currentTimeMillis();
    }

    public long getEpochTimestamp() {
        return epochTimestamp;
    }

    public void setEpochTimestamp(long epochTimestamp) {
        this.epochTimestamp = epochTimestamp;
    }

    public Map<String, String> getPathMappings() {
        return pathMappings;
    }

    public void setPathMappings(Map<String, String> pathMappings) {
        this.pathMappings = pathMappings;
    }

    public String getMlDsaSignature() {
        return mlDsaSignature;
    }

    public void setMlDsaSignature(String mlDsaSignature) {
        this.mlDsaSignature = mlDsaSignature;
    }
}
