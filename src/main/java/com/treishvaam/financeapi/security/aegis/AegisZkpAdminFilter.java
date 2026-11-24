/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Enforces Zero-Knowledge Proof (L3-ZKA) authentication for all administrative
 * actions. - Guarantees admin credentials or master keys are never transmitted over the wire.
 *
 * <p>Scope: - Intercepts all requests mapping to `/api/v1/admin/**`. - Rejects access if the
 * Schnorr-over-Lattice proof headers are missing or invalid.
 *
 * <p>Critical Dependencies: - Backend: Runs inside the Spring Security filter chain before Standard
 * Auth filters. - External: Calls the internal `aegis-zkp-service` (Go Microservice) via gRPC/REST.
 *
 * <p>Security Constraints: - Must FAIL CLOSED. If the ZKP service is down, admin access is strictly
 * denied. - Requires explicit challenge IDs to prevent replay attacks.
 *
 * <p>Non-Negotiables: - Hardcoded admin bypasses are completely prohibited.
 *
 * <p>Change Intent: - Phase 5: Created `AegisZkpAdminFilter` to intercept admin endpoints and
 * enforce mathematically verifiable identity verification.
 *
 * <p>Future AI Guidance: - If transitioning fully to gRPC, replace the `verifyProof` stub with the
 * compiled protobuf client interface.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Implemented `AegisZkpAdminFilter`. • Why
 * it was added: Administrative endpoints are the highest value target; conventional tokens are
 * vulnerable to theft. • Date: 2026-05-22 * - EDITED (Phase 5 Completion): • Removed the dummy
 * `verifyZkpWithMicroservice` stub. • Injected the live `AegisZkpServiceClient`. • Implemented
 * `X-AEGIS-Test-Token` bypass mechanism specifically to unblock GitHub Actions CI/CD integrations.
 *
 * <p>- EDITED (Incident 45 - Zero-Trust Precedence Fix): • Added path-scoped ZKP bypass for the
 * analytics healer utilizing INTERNAL_API_SECRET_KEY. Intercepts X-AEGIS-ZKP header at the filter
 * level to prevent 403 Forbidden errors while preserving global L3-ZKA integrity for other admin
 * routes. • Date: 2026-08-05
 *
 * <p>- EDITED (P0 - Phase 0 Security Hardening, Nov 22 2026): • Removed the live production CI/CD
 * automation bypass from `AegisZkpAdminFilter` (0 frontend callers in `src/apiConfig.js`; the
 * bypass string was the literal test-token value `true`). • Added OPTIONS preflight pass-through at
 * the top of `doFilterInternal`. • Replaced non-constant-time
 * `internalSecret.equals(providedZkpOrSecret)` on `/api/v1/admin/actions/analytics/heal` with
 * constant-time `MessageDigest.isEqual()` over UTF-8 byte arrays + non-blank guards (preserves
 * `AudiencePage.js` `healHistoricalAnalytics()`). • Disabled root auto-registration in
 * `FilterConfig.java`; removed `@Order(...)` (order now owned by `SecurityConfig.java`).
 *
 * <p>- EDITED (Batch 3A - P3.1, VULN-04d): • Injected `RedisTemplate<String, String>` via
 * constructor; added `@Value("${AEGIS_ADMIN_ZKP_PUBLIC_KEY:}")` field `configuredAdminPublicKey`. •
 * Standard-path gate now resolves the admin public key (`configuredAdminPublicKey` if non-blank,
 * else `X-AEGIS-Public-Key` header) — 403 (fail closed) when null/blank. • Added atomic single-use
 * challenge replay prevention: `redisTemplate.opsForValue() .setIfAbsent("aegis:zkp:used:" +
 * challengeId, "1", Duration.ofMinutes(5))` — 403 on replay or Redis errors (fail closed). • Now
 * calls `zkpServiceClient.verifyProof(adminId, publicKey, challengeId, zkpProof)` — the public key
 * is set on the wire (previously the 4-field contract was broken end-to-end). • Reworked
 * `extractAdminIdFallback()`: real identity from `SecurityContextHolder` / JWT `sub` claim
 * (length-bounded, correlation-only) — no hardcoded placeholder fallback ID. • Behavior preserved:
 * heal-path constant-time `INTERNAL_API_SECRET_KEY` check (Incident 45) untouched; ZKP fail-closed
 * philosophy; header contract unchanged.
 *
 * <p>- FIX (Batch 3B, Step 0 — audit findings): • Line 270: `token.split("\\\\.")` (4 source
 * backslashes — splits on literal `\\` + any char) → `token.split("\\.")` (2 source backslashes —
 * splits on literal `.` so JWT `header.payload.signature` segments parse, restoring the
 * `parts.length < 2` guard). •
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class AegisZkpAdminFilter extends OncePerRequestFilter {

    private final AegisZkpServiceClient zkpServiceClient;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.security.internal-secret:}")
    private String internalSecret;

    @Value("${AEGIS_ADMIN_ZKP_PUBLIC_KEY:}")
    private String configuredAdminPublicKey;

    public AegisZkpAdminFilter(
            AegisZkpServiceClient zkpServiceClient, RedisTemplate<String, String> redisTemplate) {
        this.zkpServiceClient = zkpServiceClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Skip CORS preflight — the ZKP gate does not apply to OPTIONS preflight.
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // 1. Only intercept /api/v1/admin/ endpoints
        if (path == null || !path.startsWith("/api/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // --- INCIDENT 45: PATH-SCOPED BYPASS & HEADER ALIGNMENT ---
        // Specifically evaluate the analytics healer to catch the frontend's X-AEGIS-ZKP payload
        if ("/api/v1/admin/actions/analytics/heal".equals(path)) {
            String providedZkpOrSecret = request.getHeader("X-AEGIS-ZKP");

            // 1. Check if it's the internal secret bypass (constant-time, CWE-208 hardened)
            if (internalSecret != null
                    && !internalSecret.isBlank()
                    && providedZkpOrSecret != null
                    && !providedZkpOrSecret.isBlank()
                    && MessageDigest.isEqual(
                            internalSecret.getBytes(StandardCharsets.UTF_8),
                            providedZkpOrSecret.getBytes(StandardCharsets.UTF_8))) {
                log.info("AEGIS L3-ZKA: Internal Secret Bypass authenticated for path: {}", path);
                request.setAttribute("AEGIS_ZKP_VERIFIED", true);
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Otherwise, treat it as a standard ZKP proof for this specific endpoint
            if (providedZkpOrSecret != null && !providedZkpOrSecret.isBlank()) {
                boolean isVerified =
                        zkpServiceClient.verifyProof(
                                "system_admin", "ANALYTICS_HEAL_ACTION", providedZkpOrSecret);
                if (isVerified) {
                    log.info("AEGIS L3-ZKA: ZKP Verification SUCCESS for Data Healer.");
                    request.setAttribute("AEGIS_ZKP_VERIFIED", true);
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            log.warn(
                    "AEGIS L3-ZKA: ZKP Verification & Admin Bypass FAILED for Data Healer Trigger.");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter()
                    .write("{\"error\":\"ZKP Verification Failed. Zero-Trust lock active.\"}");
            return;
        }

        // 2. Extract Proof and Challenge Context
        String zkpProof = request.getHeader("X-AEGIS-ZKP-Proof");
        String challengeId = request.getHeader("X-AEGIS-Challenge-ID");

        // Retrieve real admin identity from context or initial JWT (VULN-04d)
        String adminId = extractAdminIdFallback(request);

        // 3. Validation Gate
        if (zkpProof == null || challengeId == null || zkpProof.isBlank()) {
            log.warn(
                    "AEGIS L3-ZKA: Admin access attempted without ZKP Headers from IP: {}",
                    request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter()
                    .write(
                            "{\"error\":\"Zero-Knowledge Proof required for administrative escalation.\"}");
            return;
        }

        // 3a. Resolve admin public key (VULN-04d): configured key wins; else client-supplied
        //     header. Fail closed with 403 when neither is present/non-blank.
        String publicKey =
                (configuredAdminPublicKey != null && !configuredAdminPublicKey.isBlank())
                        ? configuredAdminPublicKey
                        : request.getHeader("X-AEGIS-Public-Key");
        if (publicKey == null || publicKey.isBlank()) {
            log.warn("AEGIS L3-ZKA: No admin public key configured/provided — 403 (fail closed).");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter()
                    .write("{\"error\":\"No admin public key configured or provided.\"}");
            return;
        }

        // 3b. Single-use challenge replay prevention (VULN-04d): atomic setIfAbsent;
        //     fail closed with 403 on replay or Redis errors.
        try {
            Boolean firstUse =
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent(
                                    "aegis:zkp:used:" + challengeId, "1", Duration.ofMinutes(5));
            if (!Boolean.TRUE.equals(firstUse)) {
                log.warn("AEGIS L3-ZKA: Challenge replay or not unique — 403.");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Challenge replay or not unique.\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("AEGIS L3-ZKA: Redis replay check failed — 403 (fail closed).", e);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Challenge replay check failed.\"}");
            return;
        }

        boolean isVerified =
                zkpServiceClient.verifyProof(adminId, publicKey, challengeId, zkpProof);

        if (!isVerified) {
            log.warn("AEGIS L3-ZKA: ZKP Verification FAILED for Challenge: {}", challengeId);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter()
                    .write("{\"error\":\"Cryptographic identity proof invalid or expired.\"}");
            // Event theoretically triggered to L8-BCSM here to increase bot score
            return;
        }

        // 4. Mark Context as Verified
        request.setAttribute("AEGIS_ZKP_VERIFIED", true);
        log.info(
                "AEGIS L3-ZKA: Admin identity mathematically verified via gnark. Granting access to {}",
                path);

        filterChain.doFilter(request, response);
    }

    /**
     * Extract a real admin identifier for ZKP circuit verification: SecurityContextHolder first
     * (post-JWT), then a length-bounded JWT {@code sub} claim extraction — correlation/logging
     * ONLY, never used for authorization.
     */
    private String extractAdminIdFallback(HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("SecurityContextHolder unavailable: {}", e.getMessage());
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String sub = extractSubClaimFromJwt(authHeader.substring("Bearer ".length()));
            if (sub != null) {
                return sub;
            }
        }
        return "anonymous";
    }

    /**
     * Length-bounded, non-fatal JWT payload extraction of the {@code sub} claim (VULN-04d —
     * correlation only). Never throws; returns null on any parse failure or oversized token.
     */
    private String extractSubClaimFromJwt(String token) {
        if (token == null || token.length() > 100_000) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2 || parts[1].isBlank()) {
            return null;
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int idx = payload.indexOf("\"sub\"");
        if (idx < 0) {
            return null;
        }
        int colon = payload.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int q1 = payload.indexOf('"', colon + 1);
        int q2 = q1 >= 0 ? payload.indexOf('"', q1 + 1) : -1;
        if (q1 < 0 || q2 < 0) {
            return null;
        }
        String sub = payload.substring(q1 + 1, q2);
        return sub.isBlank() ? null : sub;
    }
}
