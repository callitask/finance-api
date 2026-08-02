/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 2 Polymorphic Protocol Obfuscation (L2-PPO). - Rotates API endpoints
 * dynamically every 24 hours.
 *
 * <p>Scope: - Maintains a thread-safe map of Canonical Paths (e.g., /api/v1/user) to Temporal Paths
 * (e.g., /api/v1/data-node/7f3a). - Exposes resolution methods for Nginx/Cloudflare workers.
 *
 * <p>Critical Dependencies: - AegisEntropyManager (for generating truly random temporal suffixes).
 * - AegisPqcJwtService (for signing the manifest JSON before distributing to CF Workers). -
 * CloudflareEdgeSyncService (for actively pushing manifest state to Edge KV).
 *
 * <p>Security Constraints: - SEO-critical routes (/sitemap.xml, /blog/*, /market/*) MUST be
 * explicitly excluded from temporal rotation to preserve search engine integrity.
 *
 * <p>Non-Negotiables: - Manifest generation must be deterministic and fully atomic.
 *
 * <p>Change Intent: - Initial temporal mapping layer creation.
 *
 * <p>Future AI Guidance: - When Cloudflare Workers KV bindings are fully deployed, implement the
 * push mechanism directly in this class's `rotateManifest()` method.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Core map generation logic. • SEO
 * exclusion lists. • Phase 1/2 Orchestration Batch. - EDITED (MTD Sync & Prefix Fix): • ADDED
 * `cloudflareEdgeSyncService.pushManifestToCloudflareKv(rawJson)` to `rotateManifest()` to close
 * the Airgap and actively sync state to the Edge worker. • FIXED `resolveCanonicalPath()` to use
 * `.startsWith()` and `.replaceFirst()` instead of exact string matching, resolving the post-login
 * infinite deception loop. - EDITED (BUG_FIX_REPORT_001 - Public Telemetry Beacon Exemption): •
 * Added explicit prefix bypass in `resolveCanonicalPath()` for `/api/v1/analytics/event` and
 * `/api/v1/aegis/telemetry`. Why: Public telemetry beacons hitting the canonical path were matching
 * the `/api/v1/analytics` obfuscation target prefix, triggering false-positive "DECEPTION" canary
 * token poisoning and breaking analytics collection. - EDITED (Incident 28 - MTD Base Path
 * Resolution & String Collision Fix): • Upgraded `resolveCanonicalPath()` string-matching logic to
 * use strict equality (`.equals()`) and path boundary matching (`.startsWith(prefix + "/")`). Why:
 * Previous `.startsWith()` implementation failed edge-cases on exact base paths (resulting in 500
 * Internal Server Errors) and introduced severe substring collision vulnerabilities where a route
 * like `/api/v1/admin-panel` could falsely trigger the `/api/v1/admin` deception honeypot.
 */
package com.treishvaam.financeapi.security.aegis.mtd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.security.aegis.crypto.AegisEntropyManager;
import com.treishvaam.financeapi.security.aegis.crypto.AegisPqcJwtService;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AegisTemporalPathManager {

    private static final Logger log = LoggerFactory.getLogger(AegisTemporalPathManager.class);

    // SEO Routes that must NEVER be obfuscated
    private static final Set<String> SEO_EXCLUSIONS =
            Set.of(
                    "/sitemap.xml",
                    "/sitemap-dynamic",
                    "/market",
                    "/blog",
                    "/category",
                    "/api/v1/public");

    // List of target endpoints to obfuscate
    private static final String[] OBFUSCATION_TARGETS = {
        "/api/v1/admin", "/api/v1/auth", "/api/v1/users", "/api/v1/dashboard", "/api/v1/analytics"
    };

    private final AegisEntropyManager entropyManager;
    private final AegisPqcJwtService pqcJwtService;
    private final CloudflareEdgeSyncService cloudflareEdgeSyncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map: Canonical -> Temporal
    private final Map<String, String> currentManifest = new ConcurrentHashMap<>();
    // Map: Temporal -> Canonical (for fast reverse lookup)
    private final Map<String, String> reverseLookup = new ConcurrentHashMap<>();

    private String signedManifestJson = "";

    public AegisTemporalPathManager(
            AegisEntropyManager entropyManager,
            AegisPqcJwtService pqcJwtService,
            CloudflareEdgeSyncService cloudflareEdgeSyncService) {
        this.entropyManager = entropyManager;
        this.pqcJwtService = pqcJwtService;
        this.cloudflareEdgeSyncService = cloudflareEdgeSyncService;
    }

    @PostConstruct
    public void init() {
        rotateManifest();
    }

    public void rotateManifest() {
        log.info("AEGIS L2-PPO: Rotating Temporal Endpoint Manifest...");
        currentManifest.clear();
        reverseLookup.clear();

        for (String target : OBFUSCATION_TARGETS) {
            String temporalSuffix = generateRandomHex(8);
            String temporalPath = "/api/v1/node/" + temporalSuffix; // Obfuscated structure

            currentManifest.put(target, temporalPath);
            reverseLookup.put(temporalPath, target);
        }

        try {
            // Create a JSON representation and sign it with PQC for the Frontend/Workers
            Map<String, Object> manifestData =
                    Map.of("paths", currentManifest, "issuedAt", Instant.now().toEpochMilli());

            String rawJson = objectMapper.writeValueAsString(manifestData);
            // Envelope it in a PQC signature to prevent Man-in-the-Middle manifest poisoning
            this.signedManifestJson = pqcJwtService.issueHybridToken(rawJson, "SYSTEM", "MANIFEST");

            // Push actively to Cloudflare Edge KV
            cloudflareEdgeSyncService.pushManifestToCloudflareKv(rawJson);

            log.info("AEGIS L2-PPO: Manifest rotation complete. Secured with ML-DSA-87.");
        } catch (Exception e) {
            log.error("AEGIS L2-PPO: Failed to build signed manifest!", e);
        }
    }

    public String resolveCanonicalPath(String requestUri) {
        // Bypass SEO routes immediately (Safe boundary match)
        for (String exclusion : SEO_EXCLUSIONS) {
            if (requestUri.equals(exclusion) || requestUri.startsWith(exclusion + "/")) {
                return requestUri;
            }
        }

        // Explicitly bypass public telemetry beacons from MTD deception evaluation
        if (requestUri.startsWith("/api/v1/analytics/event")
                || requestUri.startsWith("/api/v1/aegis/telemetry")) {
            return requestUri;
        }

        // If it's a temporal path, resolve to canonical using safe exact or slash-suffixed prefix
        // matching
        for (Map.Entry<String, String> entry : reverseLookup.entrySet()) {
            String temporalPrefix = entry.getKey();
            String canonicalTarget = entry.getValue();

            if (requestUri.equals(temporalPrefix)) {
                return canonicalTarget;
            } else if (requestUri.startsWith(temporalPrefix + "/")) {
                return requestUri.replaceFirst(temporalPrefix, canonicalTarget);
            }
        }

        // If it's trying to access a canonical path directly when it should be obfuscated
        // Upgraded to secure boundary match to prevent substring collision traps
        for (String target : OBFUSCATION_TARGETS) {
            if (requestUri.equals(target) || requestUri.startsWith(target + "/")) {
                return "DECEPTION"; // Triggers L4-ADA
            }
        }

        return requestUri; // Fallback for unmatched routes
    }

    public String getSignedManifestJson() {
        return signedManifestJson;
    }

    private String generateRandomHex(int length) {
        byte[] bytes = new byte[length / 2];
        entropyManager.getSecureRandom().nextBytes(bytes);
        return String.format("%0" + length + "x", new BigInteger(1, bytes));
    }
}
