package com.treishvaam.financeapi.security.aegis.mtd;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Executes AEGIS Phase 4 (Moving Target Defense). - Closes the "Airgap" by syncing
 * locally detected threats (L4-ADA, L5-BIE) to the Cloudflare Edge KV.
 *
 * <p>Scope: - Issues HTTP API calls to Cloudflare v4 KV storage to globally block IPs and JA3
 * fingerprints. - Enforces strict Free-Tier API limit protections.
 *
 * <p>Critical Dependencies: - Java 21 HttpClient & Virtual Threads. - Cloudflare Environment
 * Variables (Account ID, Namespace ID, API Token).
 *
 * <p>Security Constraints: - Must never hardcode API keys. - MUST enforce expiration TTLs (86400s /
 * 24h) to avoid permanent IP bans (ISP dynamic IP reuse). - MUST protect Cloudflare free-tier write
 * limits (1,000/day) by preventing duplicate API calls.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 4): • Initial creation of
 * Cloudflare KV Sync. • Implemented in-memory `localBlockCache` to deduplicate API writes and
 * protect Free Tier billing quotas. • Configured Java 21 Virtual Threads for non-blocking outbound
 * HTTP requests. - EDITED (MTD Sync Addition): • Added `pushManifestToCloudflareKv` to distribute
 * the daily MTD manifest json directly to the Edge.
 */
@Service
public class CloudflareEdgeSyncService {

    private static final Logger logger = LoggerFactory.getLogger(CloudflareEdgeSyncService.class);

    @Value("${CLOUDFLARE_ACCOUNT_ID:}")
    private String accountId;

    @Value("${CLOUDFLARE_THREAT_KV_NAMESPACE_ID:}")
    private String namespaceId;

    @Value("${CLOUDFLARE_API_TOKEN:}")
    private String apiToken;

    private final HttpClient httpClient;

    // Free-Tier Protection: Prevent spamming the CF API for the same attacker
    private final ConcurrentHashMap<String, Instant> localBlockCache = new ConcurrentHashMap<>();
    private static final long BLOCK_DURATION_SECONDS = 86400; // 24 Hours

    public CloudflareEdgeSyncService() {
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .build();
    }

    public void blockAttackerAtEdge(String ip, String ja3, String reason) {
        if (apiToken == null || apiToken.isBlank()) {
            logger.warn("Cloudflare API Token not configured. Edge sync skipped.");
            return;
        }

        // 1. Check Free-Tier Protection Cache
        Instant now = Instant.now();
        if (ip != null && !ip.equals("UNKNOWN")) {
            Instant expiry = localBlockCache.get(ip);
            if (expiry == null || now.isAfter(expiry)) {
                pushToCloudflareKv("aegis:block:" + ip, reason);
                localBlockCache.put(ip, now.plusSeconds(BLOCK_DURATION_SECONDS));
            }
        }

        if (ja3 != null && !ja3.equals("UNKNOWN")) {
            Instant expiry = localBlockCache.get("ja3:" + ja3);
            if (expiry == null || now.isAfter(expiry)) {
                pushToCloudflareKv("aegis:block:ja3:" + ja3, reason);
                localBlockCache.put("ja3:" + ja3, now.plusSeconds(BLOCK_DURATION_SECONDS));
            }
        }
    }

    public void pushManifestToCloudflareKv(String manifestJson) {
        if (apiToken == null || apiToken.isBlank()) {
            logger.warn("Cloudflare API Token not configured. Manifest sync skipped.");
            return;
        }

        try {
            String url =
                    String.format(
                            "https://api.cloudflare.com/client/v4/accounts/%s/storage/kv/namespaces/%s/values/aegis:mtd:manifest",
                            accountId, namespaceId);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + apiToken)
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(manifestJson))
                            .build();

            httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(
                            response -> {
                                if (response.statusCode() == 200) {
                                    logger.info(
                                            "AEGIS Edge Sync: Successfully pushed MTD Manifest to Cloudflare KV");
                                } else {
                                    logger.error(
                                            "AEGIS Edge Sync Manifest Push Failed: HTTP {} - {}",
                                            response.statusCode(),
                                            response.body());
                                }
                            })
                    .exceptionally(
                            ex -> {
                                logger.error("AEGIS Edge Sync Exception for manifest", ex);
                                return null;
                            });

        } catch (Exception e) {
            logger.error("Failed to construct Cloudflare KV manifest push request", e);
        }
    }

    private void pushToCloudflareKv(String key, String reason) {
        try {
            String url =
                    String.format(
                            "https://api.cloudflare.com/client/v4/accounts/%s/storage/kv/namespaces/%s/values/%s?expiration_ttl=%d",
                            accountId, namespaceId, key, BLOCK_DURATION_SECONDS);

            String payload =
                    String.format(
                            "{\"blocked\": true, \"reason\": \"%s\", \"timestamp\": \"%s\"}",
                            reason, Instant.now().toString());

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + apiToken)
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

            // Execute asynchronously on Virtual Thread
            httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(
                            response -> {
                                if (response.statusCode() == 200) {
                                    logger.info(
                                            "AEGIS Edge Sync: Successfully pushed threat {} to Cloudflare KV",
                                            key);
                                } else {
                                    logger.error(
                                            "AEGIS Edge Sync Failed for {}: HTTP {} - {}",
                                            key,
                                            response.statusCode(),
                                            response.body());
                                }
                            })
                    .exceptionally(
                            ex -> {
                                logger.error("AEGIS Edge Sync Exception for key {}", key, ex);
                                return null;
                            });

        } catch (Exception e) {
            logger.error("Failed to construct Cloudflare KV push request", e);
        }
    }
}
