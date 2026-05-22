/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Computes and verifies HMAC-SHA256 integrity signatures for published BlogPost
 * content. - Implements the "Digital Content Signing" enterprise security requirement.
 *
 * <p>Scope: - Responsible for: HMAC computation, hex encoding, signature verification. - Must NEVER
 * be responsible for: key storage, post persistence, routing decisions.
 *
 * <p>Critical Dependencies: - Backend: Requires env var CONTENT_SIGNING_KEY (Base64-encoded minimum
 * 32-byte key). - Called by BlogPostServiceImpl.publishPost() for signing. - Called by
 * BlogPostServiceImpl public read methods for verification.
 *
 * <p>Security Constraints: - Key MUST come exclusively from CONTENT_SIGNING_KEY env var. NEVER
 * hardcoded. - Uses HmacSHA256 — requires the secret key to verify, unlike plain SHA256 hashes. -
 * Signature payload: title + "|" + slug + "|" + content + "|" + author + "|" + tenantId The "|"
 * separator prevents field-boundary collision attacks. - If CONTENT_SIGNING_KEY is missing: log
 * WARN and operate in disabled mode. Do NOT throw at startup — prevents deployment breakage during
 * key rollout. - Verification failure: log ERROR (tamper detected), return false. Never throw.
 *
 * <p>Non-Negotiables: - Disabled mode (missing key) must transparently pass all verifications
 * (return true). - This preserves backward compatibility during rollout. - The content_signature
 * column value is NEVER sent to the frontend API response.
 *
 * <p>Future AI Guidance: - For key rotation: add a CONTENT_SIGNING_KEY_V2 env var, try V2 first,
 * fall back to V1. - Do NOT add signature verification to draft reads — only published content
 * needs this. - The RabbitMQ alert event on tamper detection is a FUTURE enhancement (commented
 * below).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Phase 2 — Content Integrity): • Created
 * ContentIntegrityService with HMAC-SHA256 signing and verification. • Why: Implements DB-level
 * tamper detection for published financial content. • Startup behavior: WARN (not throw) when key
 * missing — safe rollout. • Date: Phase 2 Security Implementation
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY must never be deleted, truncated, or
 * regenerated.
 */
package com.treishvaam.financeapi.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContentIntegrityService {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityService.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec signingKey;
    private final boolean enabled;

    public ContentIntegrityService() {
        String encodedKey = System.getenv("CONTENT_SIGNING_KEY");
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            logger.warn(
                    "[ContentIntegrityService] CONTENT_SIGNING_KEY is not set. "
                            + "Content integrity signing is DISABLED. Set this env var to enable "
                            + "database tamper detection on published articles.");
            this.signingKey = null;
            this.enabled = false;
            return;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "[ContentIntegrityService] CONTENT_SIGNING_KEY is not valid Base64. "
                            + "Signing disabled.",
                    e);
            this.signingKey = null;
            this.enabled = false;
            return;
        }
        if (keyBytes.length < 32) {
            logger.warn(
                    "[ContentIntegrityService] CONTENT_SIGNING_KEY should be at least "
                            + "32 bytes. Got {} bytes. Signing disabled.",
                    keyBytes.length);
            this.signingKey = null;
            this.enabled = false;
            return;
        }
        this.signingKey = new SecretKeySpec(keyBytes, ALGORITHM);
        this.enabled = true;
        logger.info("[ContentIntegrityService] HMAC-SHA256 content signing ENABLED.");
    }

    /**
     * Compute HMAC-SHA256 signature over critical post fields. Payload:
     * title|slug|content|author|tenantId (pipe-separated to prevent collision attacks).
     *
     * @return lowercase hex HMAC string, or null if signing is disabled/failed
     */
    public String computeSignature(
            String title, String slug, String content, String author, String tenantId) {
        if (!enabled || signingKey == null) return null;
        try {
            String payload =
                    safe(title)
                            + "|"
                            + safe(slug)
                            + "|"
                            + safe(content)
                            + "|"
                            + safe(author)
                            + "|"
                            + safe(tenantId);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) {
            logger.error("[ContentIntegrityService] Signature computation failed.", e);
            return null;
        }
    }

    /**
     * Verify a stored signature against current post content.
     *
     * <p>Returns TRUE if: (a) Signing is disabled — transparent pass-through (b) storedSignature is
     * null — pre-signature era post, backward compat (c) Computed signature matches stored
     * signature exactly
     *
     * <p>Returns FALSE ONLY on confirmed tamper detection.
     */
    public boolean verifySignature(
            String storedSignature,
            String title,
            String slug,
            String content,
            String author,
            String tenantId) {
        if (!enabled || signingKey == null) return true;
        if (storedSignature == null) return true; // Pre-signing era — backward compat

        String computed = computeSignature(title, slug, content, author, tenantId);
        if (computed == null) return true; // Computation failure — fail open for reads

        boolean match = computed.equals(storedSignature);
        if (!match) {
            logger.error(
                    "[ContentIntegrityService] ⚠️ CONTENT TAMPER DETECTED! "
                            + "Post slug='{}' — stored signature does not match current content. "
                            + "Possible unauthorized database modification. "
                            + "Investigate immediately via Grafana audit logs.",
                    slug);
            // FUTURE: Publish a RabbitMQ alert event here:
            // messagePublisher.publish("security.alerts", new TamperAlertEvent(slug));
        }
        return match;
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
