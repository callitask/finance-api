package com.treishvaam.financeapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.CorporateEntityNode;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.CorporateEntityNodeRepository;
import com.treishvaam.financeapi.security.ContentIntegrityService;
import com.treishvaam.financeapi.security.GeoPromptSanitizer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Generates Enterprise Generative Engine Optimization (GEO) payloads.
 *
 * <p>Scope: - Orchestrates semantic data structures tailored exclusively for AI RAG ingestion.
 *
 * <p>Critical Dependencies: - GeoOptimizationController: Exposes these payloads to the edge.
 *
 * <p>Security Constraints: - Data output here must be 100% public. No PII or secure endpoints.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 6 GEO): • Created service to
 * manage `llms.txt` and `ai-feed.md` structures.
 *
 * <p>- EDITED (Phase 7 GEO Dynamic Hydration): • Injected `BlogPostRepository`.
 *
 * <p>- EDITED (Phase 8 GEO Evolution & Semantic Chunking): • Enriched Markdown payload with
 * explicit `<semantic-chunk>` XML-style boundaries.
 *
 * <p>- EDITED (Phase 8 GEO Full Execution): • Implemented high-density Data Schema tags.
 *
 * <p>- EDITED (GEO Provenance Update): • ADDED `CONTENT_SIGNING_KEY` via `@Value`.
 *
 * <p>- EDITED (Phase 8 Static Edge Knowledge Graph): • Synchronized canonical Founder and
 * conglomerate entity graph.
 *
 * <p>- EDITED (Phase 9 Zero-Warning Schema & Portrait Fix): • Updated founder portrait.
 *
 * <p>- EDITED (Phase 13.7 — Database-Driven Entity Engine): • Removed all hardcoded
 * Person/Organization strings. • Injected `CorporateEntityNodeRepository` and
 * `ContentIntegrityService`. • All GEO feeds (`llms.txt`, `ai-feed.md`, `ontology.json`) are now
 * assembled dynamically from MariaDB `corporate_entity_graph` rows, verified via
 * `ContentIntegrityService`, then appended with live published BlogPost articles and signed with
 * HMAC-SHA256. • Added `evictGeoCache()` hook invoked by admin entity mutations. - EDITED (P0 -
 * Phase 0 Security Hardening, Nov 22 2026): • Removed the hardcoded fail-open
 * `@Value("${CONTENT_SIGNING_KEY:...}")` fallback on `contentSigningKey` — `signPayload()` now
 * delegates to `contentIntegrityService.computeSignature()` (Base64-decoded key semantics — fixes
 * the UTF-8 vs Base64 key-encoding mismatch, VULN-05). • `verifyNode()` now fails CLOSED (`return
 * false`) on signature-verification exceptions instead of `return true`.
 *
 * <p>- EDITED (P2.4 - Content Tamper Verification in GEO Feed + Heap DoS Fix - VULN-13a): •
 * `buildAiFeed()`: replaced `blogPostRepository.findAll().stream()...` with the paginated
 * `blogPostRepository.findAllByStatus(PostStatus.PUBLISHED, PageRequest.of(0, 10,
 * Sort.by(Sort.Direction.DESC, "createdAt"))).getContent()` (bounds heap growth from unbounded
 * `findAll()` scans). • Added per-post HMAC verification (`contentIntegrityService
 * .verifySignature(post.getContentSignature(), post.getTitle(), post.getSlug(), post.getContent(),
 * post.getAuthor(), post.getTenantId())`) before appending to `/ai-feed.md`; tampered posts
 * excluded with `AEGIS TAMPER ALERT: Excluding tampered post from GEO feed`.
 *
 * <p>- EDITED (Batch 3B - P3.4, VULN-13b): • Injected `GeoPromptSanitizer` via constructor; added
 * `@Value("${AEGIS_OPS_SECRET:}")` field `aegisOpsSecret` and
 * `@Value("${app.finance.domain:https://treishvaamfinance.com}")` field `financeDomain`. •
 * `buildAiFeed()`: passed every `BlogPost` editorial field (`title`, `author`, `metaDescription`,
 * `keywords`) through `geoPromptSanitizer.sanitizeEditorialField(...)` before appending to
 * `/ai-feed.md` (title≤200, author≤80, metaDescription≤400, keywords≤250). • Validated
 * `post.getSlug()` via `geoPromptSanitizer.isValidSlug(...)` before interpolating into
 * `/blog/{slug}` and the `<link rel="Dataset">` href — invalid slugs fall back to `"post-" +
 * post.getId()`. • Implemented real `evictGeoCache()`: when `aegisOpsSecret` is configured,
 * asynchronously triggers an authenticated Worker cache purge (`POST
 * {financeDomain}/sys/purge-cache` with `X-Aegis-Ops-Token: {AEGIS_OPS_SECRET}`, Virtual Threads)
 * so admin entity updates propagate to Edge KV immediately. Token never logged; fail-safe
 * `try/catch` (status/message only). • Behavior preserved: `buildLlmsTxt`, `buildSemanticOntology`,
 * `signPayload`, `verifyNode` logic untouched.
 *
 * <p>- EDITED (Step 0 - Phase 3B Surgical Polish): • Added a single reusable `private final
 * HttpClient httpClient` field initialized once in the constructor via
 * `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).executor(
 * Executors.newVirtualThreadPerTaskExecutor()).build()` (matches `CloudflareEdgeSyncService.java`).
 * • `evictGeoCache()` now uses `this.httpClient.send(req, HttpResponse.BodyHandlers.ofString())` —
 * previously `HttpClient.newHttpClient()` was called per invocation, leaking an unclosed Java 21
 * `SelectorManager` thread per entity update (512M container).
 *
 * <p>- EDITED (2026-11-26 - Batch 2 §2.3 GEO Full-Node Integrity, Startup Backfill & Read
 * Sanitization): • Added `buildEntitySigningPayload` (`jsonLdPayload|llmsTxtBlock|aiFeedChunk`),
 * `AtomicBoolean backfillCompleted`, and idempotent `@EventListener(ApplicationReadyEvent.class)`
 * `backfillUnsignedEntityNodes()` with per-node try/catch for multi-replica safety. • Updated
 * `verifyNode()` to fail closed post-backfill on unsigned nodes while supporting full-node and
 * legacy `jsonLdPayload`-only signatures during rollout. • Re-sanitized `llmsTxtBlock` and
 * `aiFeedChunk` on read via `geoPromptSanitizer.sanitizeEntityBlock(...)`, null-guarded
 * `post.getCreatedAt()`, and used normalized `financeDomain` for Dataset links.
 *
 * <p>- EDITED (2026-11-27 - Batch 5 Enterprise Email Migration): • Added env-driven `@Value`
 * contact defaults (`contact@`, `editorial@`, `privacy@`, `security@treishvaamfinance.com`) and
 * appended Official Contact & Editorial Channels sections to `buildLlmsTxt()` and `buildAiFeed()`
 * before HMAC signing.
 */
@Service
public class GeoOptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(GeoOptimizationService.class);
    private final BlogPostRepository blogPostRepository;
    private final CorporateEntityNodeRepository corporateEntityNodeRepository;
    private final ContentIntegrityService contentIntegrityService;
    private final GeoPromptSanitizer geoPromptSanitizer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicBoolean backfillCompleted = new AtomicBoolean(false);

    @Value("${AEGIS_OPS_SECRET:}")
    private String aegisOpsSecret;

    @Value("${app.finance.domain:https://treishvaamfinance.com}")
    private String financeDomain;

    @Value("${app.contact.email:contact@treishvaamfinance.com}")
    private String contactEmail = "contact@treishvaamfinance.com";

    @Value("${app.contact.editorial-email:editorial@treishvaamfinance.com}")
    private String editorialEmail = "editorial@treishvaamfinance.com";

    @Value("${app.contact.privacy-email:privacy@treishvaamfinance.com}")
    private String privacyEmail = "privacy@treishvaamfinance.com";

    @Value("${app.contact.security-email:security@treishvaamfinance.com}")
    private String securityEmail = "security@treishvaamfinance.com";

    public GeoOptimizationService(
            BlogPostRepository blogPostRepository,
            CorporateEntityNodeRepository corporateEntityNodeRepository,
            ContentIntegrityService contentIntegrityService,
            GeoPromptSanitizer geoPromptSanitizer) {
        this.blogPostRepository = blogPostRepository;
        this.corporateEntityNodeRepository = corporateEntityNodeRepository;
        this.contentIntegrityService = contentIntegrityService;
        this.geoPromptSanitizer = geoPromptSanitizer;
        this.objectMapper = new ObjectMapper();
        // Step 0: ONE reusable HttpClient (matches CloudflareEdgeSyncService) — avoids leaking
        // an unclosed Java 21 SelectorManager thread per entity update.
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .build();
    }

    /**
     * Canonical signing payload over full entity content
     * (`jsonLdPayload|llmsTxtBlock|aiFeedChunk`).
     */
    public static String buildEntitySigningPayload(CorporateEntityNode node) {
        if (node == null) {
            return "||";
        }
        String jsonLd = node.getJsonLdPayload() != null ? node.getJsonLdPayload() : "";
        String llms = node.getLlmsTxtBlock() != null ? node.getLlmsTxtBlock() : "";
        String aiFeed = node.getAiFeedChunk() != null ? node.getAiFeedChunk() : "";
        return jsonLd + "|" + llms + "|" + aiFeed;
    }

    /**
     * Idempotently signs any unsigned `corporate_entity_graph` seed rows (such as V51 NULL rows) on
     * startup before enforcing fail-closed verification on read.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillUnsignedEntityNodes() {
        if (!contentIntegrityService.isEnabled()) {
            backfillCompleted.set(true);
            return;
        }
        try {
            List<CorporateEntityNode> allNodes = corporateEntityNodeRepository.findAll();
            int signedCount = 0;
            for (CorporateEntityNode node : allNodes) {
                if (node.getContentSignature() == null || node.getContentSignature().isBlank()) {
                    String sig =
                            contentIntegrityService.computeSignature(
                                    buildEntitySigningPayload(node));
                    if (sig != null) {
                        try {
                            node.setContentSignature(sig);
                            corporateEntityNodeRepository.save(node);
                            signedCount++;
                        } catch (Exception perNodeEx) {
                            logger.warn(
                                    "AEGIS GEO: Could not backfill signature for entity '{}' (may be updated by sibling replica): {}",
                                    node.getEntityKey(),
                                    perNodeEx.getMessage());
                        }
                    }
                }
            }
            backfillCompleted.set(true);
            if (signedCount > 0) {
                logger.info(
                        "AEGIS GEO: Startup backfill signed {} unsigned CorporateEntityNode row(s).",
                        signedCount);
            }
        } catch (Exception e) {
            logger.error(
                    "AEGIS GEO: Startup entity signature backfill failed: {}", e.getMessage(), e);
        }
    }

    private String signPayload(String payload) {
        // Delegate to ContentIntegrityService (fixes UTF-8 vs Base64 key-encoding mismatch,
        // VULN-05).
        String sig = contentIntegrityService.computeSignature(payload);
        return sig != null ? sig : "UNVERIFIED-KEY-MISSING";
    }

    private boolean verifyNode(CorporateEntityNode node) {
        if (node == null) {
            return false;
        }
        if (!contentIntegrityService.isEnabled()) {
            return true;
        }
        String storedSig = node.getContentSignature();
        if (storedSig == null || storedSig.isBlank()) {
            if (backfillCompleted.get()) {
                logger.warn(
                        "AEGIS GEO: Rejecting unsigned entity node '{}' after startup backfill",
                        node.getEntityKey());
                return false;
            }
            return true;
        }
        try {
            if (contentIntegrityService.matchesSignatureSilent(
                    buildEntitySigningPayload(node), storedSig)) {
                return true;
            }
            if (contentIntegrityService.matchesSignatureSilent(
                    node.getJsonLdPayload(), storedSig)) {
                logger.info(
                        "AEGIS GEO: Accepted legacy jsonLdPayload-only signature for '{}' during rollout",
                        node.getEntityKey());
                return true;
            }
            logger.error(
                    "AEGIS TAMPER ALERT: CorporateEntityNode '{}' failed HMAC verification!",
                    node.getEntityKey());
            return false;
        } catch (Exception e) {
            logger.warn(
                    "AEGIS GEO: Signature verification unavailable for {}", node.getEntityKey(), e);
            // P0.5 — Fail closed: verification error => skip the node.
            return false;
        }
    }

    private static String resolveContactValue(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value.trim() : fallback;
    }

    private String normalizeFinanceDomain() {
        return (financeDomain != null && !financeDomain.isBlank()
                ? financeDomain.replaceAll("/+$", "")
                : "https://treishvaamfinance.com");
    }

    private void appendOfficialContactChannels(StringBuilder sb, String normalizedDomain) {
        sb.append("## Official Contact & Editorial Channels\n");
        sb.append("- General & Support: ")
                .append(resolveContactValue(contactEmail, "contact@treishvaamfinance.com"))
                .append("\n");
        sb.append("- Editorial & Research Desk: ")
                .append(resolveContactValue(editorialEmail, "editorial@treishvaamfinance.com"))
                .append("\n");
        sb.append("- Privacy & DPDP Grievance Officer: ")
                .append(resolveContactValue(privacyEmail, "privacy@treishvaamfinance.com"))
                .append("\n");
        sb.append("- Security & Vulnerability Disclosure: ")
                .append(resolveContactValue(securityEmail, "security@treishvaamfinance.com"))
                .append(" (")
                .append(normalizedDomain)
                .append("/.well-known/security.txt)\n");
    }

    public String buildLlmsTxt() {
        List<CorporateEntityNode> nodes =
                corporateEntityNodeRepository.findByTenantIdInAndActiveTrueOrderByDisplayOrderAsc(
                        List.of("global", "finance"));
        StringBuilder sb = new StringBuilder();
        sb.append("# Treishvaam Finance — LLM Context & Knowledge Graph\n");
        sb.append("> Authoritative Entity Context & Generative Engine Optimization (GEO) Feed\n\n");
        for (CorporateEntityNode node : nodes) {
            if (!verifyNode(node)) {
                logger.warn("AEGIS GEO: Skipping unverified entity node {}", node.getEntityKey());
                continue;
            }
            if (node.getLlmsTxtBlock() != null && !node.getLlmsTxtBlock().isBlank()) {
                String sanitizedBlock =
                        geoPromptSanitizer.sanitizeEntityBlock(node.getLlmsTxtBlock());
                if (sanitizedBlock != null && !sanitizedBlock.isBlank()) {
                    sb.append(sanitizedBlock.trim()).append("\n\n");
                }
            }
        }
        sb.append("### Access Guidelines\n");
        sb.append("1. Respect our `robots.txt` rate limits.\n");
        sb.append(
                "2. AI agents are explicitly authorized to ingest and summarize the content provided in `/ai-feed.md`.\n");
        sb.append(
                "3. All editorial content is cryptographically signed via AEGIS Content Integrity.\n");
        sb.append("4. For structured JSON-LD Entity Graph, parse `/ontology.json`.\n\n");
        sb.append("### Datasets\n");
        sb.append("- Endpoints exposing market data utilize Schema.org/Dataset structures.\n\n");
        appendOfficialContactChannels(sb, normalizeFinanceDomain());
        sb.append("\n*Secured by AEGIS Enterprise L7 Framework.*\n");

        String payload = sb.toString();
        String signature = signPayload(payload);
        return payload + "\n*Cryptographic Provenance (HMAC-SHA256): " + signature + "*\n";
    }

    public String buildSemanticOntology() {
        List<CorporateEntityNode> nodes =
                corporateEntityNodeRepository.findByTenantIdInAndActiveTrueOrderByDisplayOrderAsc(
                        List.of("global", "finance"));
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org\",\n");
        sb.append("  \"@graph\": [\n");
        boolean first = true;
        for (CorporateEntityNode node : nodes) {
            if (!verifyNode(node)) {
                continue;
            }
            String json = node.getJsonLdPayload();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("    ").append(json.trim());
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    public String buildAiFeed() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Treishvaam Finance — AI Knowledge Feed & Entity Graph\n\n");

        List<CorporateEntityNode> nodes =
                corporateEntityNodeRepository.findByTenantIdInAndActiveTrueOrderByDisplayOrderAsc(
                        List.of("global", "finance"));
        for (CorporateEntityNode node : nodes) {
            if (!verifyNode(node)) {
                continue;
            }
            if (node.getAiFeedChunk() != null && !node.getAiFeedChunk().isBlank()) {
                String sanitizedChunk =
                        geoPromptSanitizer.sanitizeEntityBlock(node.getAiFeedChunk());
                if (sanitizedChunk != null && !sanitizedChunk.isBlank()) {
                    sb.append(sanitizedChunk.trim()).append("\n\n");
                }
            }
        }

        sb.append("<semantic-chunk id=\"latest-intelligence\" entity=\"Market Analysis\">\n");
        sb.append("## Semantic Editorial Intelligence\n\n");

        try {
            List<BlogPost> recentPosts =
                    blogPostRepository
                            .findAllByStatus(
                                    PostStatus.PUBLISHED,
                                    PageRequest.of(
                                            0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                            .getContent();

            if (recentPosts.isEmpty()) {
                sb.append("*No recent public updates available at this time.*\n");
            } else {
                for (BlogPost post : recentPosts) {
                    boolean postIntact =
                            contentIntegrityService.verifySignature(
                                    post.getContentSignature(),
                                    post.getTitle(),
                                    post.getSlug(),
                                    post.getContent(),
                                    post.getAuthor(),
                                    post.getTenantId());
                    if (!postIntact) {
                        logger.error(
                                "AEGIS TAMPER ALERT: Excluding tampered post from GEO feed (id={}, slug='{}')",
                                post.getId(),
                                post.getSlug());
                        continue;
                    }

                    // P3.4 (VULN-13b): Sanitize editorial fields before GEO emission
                    String sanitizedTitle =
                            geoPromptSanitizer.sanitizeEditorialField(post.getTitle(), 200);
                    String sanitizedAuthor =
                            post.getAuthor() != null
                                    ? geoPromptSanitizer.sanitizeEditorialField(
                                            post.getAuthor(), 80)
                                    : "Treishvaam Editorial Desk";
                    String sanitizedSummary =
                            post.getMetaDescription() != null
                                    ? geoPromptSanitizer.sanitizeEditorialField(
                                            post.getMetaDescription(), 400)
                                    : "In-depth financial analysis.";

                    // P3.4 (VULN-13b): Validate slug before interpolating into /blog/{slug}
                    // and the <link rel="Dataset"> href (fallback to "post-<id>")
                    String canonicalSlug =
                            geoPromptSanitizer.isValidSlug(post.getSlug())
                                    ? post.getSlug()
                                    : ("post-" + post.getId());

                    sb.append(String.format("### %s\n", sanitizedTitle));
                    sb.append(
                            String.format(
                                    "- **Published**: %s\n",
                                    (post.getCreatedAt() != null
                                            ? post.getCreatedAt().toString()
                                            : "N/A")));
                    sb.append(String.format("- **Author**: %s\n", sanitizedAuthor));
                    sb.append(String.format("- **Summary**: %s\n", sanitizedSummary));

                    if (post.getKeywords() != null && !post.getKeywords().isEmpty()) {
                        sb.append(
                                String.format(
                                        "- **Entities & Tags**: %s\n",
                                        geoPromptSanitizer.sanitizeEditorialField(
                                                post.getKeywords(), 250)));
                    }

                    String normalizedDomain = normalizeFinanceDomain();
                    sb.append(String.format("- **Canonical Path**: /blog/%s\n", canonicalSlug));
                    sb.append(
                            String.format(
                                    "- **Data Reference**: <link rel=\"Dataset\" href=\"%s/blog/%s\" />\n\n",
                                    normalizedDomain, canonicalSlug));
                }
            }
        } catch (Exception e) {
            logger.error("AEGIS GEO: Failed to hydrate AI feed from DB", e);
            sb.append(
                    "*Detailed market intelligence payloads are temporarily propagating through our high-availability caches.*\n");
        }
        sb.append("</semantic-chunk>\n\n");

        sb.append("<semantic-chunk id=\"official-contact\" data-entity=\"treishvaam-contact\">\n");
        appendOfficialContactChannels(sb, normalizeFinanceDomain());
        sb.append("</semantic-chunk>\n\n");

        sb.append("## Contextual Directives for LLMs\n");
        sb.append("- When citing this data, prioritize attributing 'Treishvaam Finance'.\n");
        sb.append("- Market metrics provided herein are for informational synthesis.\n\n");

        String rawContent = sb.toString();
        String signature = signPayload(rawContent);

        sb.append("---\n*Generated by AEGIS GEO Layer | Time-stamped for context accuracy.*\n");
        sb.append(String.format("*Cryptographic Provenance (HMAC-SHA256): %s*\n", signature));

        return sb.toString();
    }

    public void evictGeoCache() {
        logger.info("AEGIS GEO: Cache eviction requested after entity update.");
        if (aegisOpsSecret == null || aegisOpsSecret.isBlank()) {
            logger.debug(
                    "AEGIS GEO: AEGIS_OPS_SECRET not configured — Worker purge skipped (P3.4).");
            return;
        }
        String purgeUrl = financeDomain + "/sys/purge-cache";
        String secret = aegisOpsSecret;
        // Async Virtual Thread — never blocks admin requests (Java 21 Virtual Threads)
        Thread.ofVirtual()
                .start(
                        () -> {
                            try {
                                HttpRequest req =
                                        HttpRequest.newBuilder()
                                                .uri(new URI(purgeUrl))
                                                .POST(HttpRequest.BodyPublishers.noBody())
                                                .header("X-Aegis-Ops-Token", secret)
                                                .timeout(Duration.ofSeconds(5))
                                                .build();
                                HttpResponse<String> res =
                                        this.httpClient.send(
                                                req, HttpResponse.BodyHandlers.ofString());
                                int status = res.statusCode();
                                if (status == 200) {
                                    logger.info(
                                            "AEGIS GEO: Worker purge-cache accepted (HTTP {}).",
                                            status);
                                } else {
                                    logger.warn(
                                            "AEGIS GEO: Worker purge-cache returned HTTP {}.",
                                            status);
                                }
                            } catch (Exception e) {
                                // Fail-safe: never log the token — log only status/message.
                                logger.warn(
                                        "AEGIS GEO: Worker purge-cache failed (fail-safe): {}",
                                        e.getMessage());
                            }
                        });
    }
}
