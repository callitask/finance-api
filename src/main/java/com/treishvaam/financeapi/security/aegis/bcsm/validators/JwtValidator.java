/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 Validator (L1-PQCf Node). - Votes on consensus based on the presence
 * and validity of ML-DSA signatures.
 *
 * <p>Scope: - Parses the Authorization header; dispatches per-alg (ML-DSA-87/PQC-JWT →
 * AegisPqcJwtService; standard OAuth algs → Spring Security JwtDecoder; none/unknown → BLOCK).
 *
 * <p>Security Constraints: - A forged token immediately pushes the score to 100. - Malformed tokens
 * fail closed. - NO false positives on standard Keycloak OIDC JWTs (RS256 et al.).
 *
 * <p>Non-Negotiables: - Must not throw exceptions on missing/malformed tokens (anonymous access is
 * valid for some routes). - Length-bounded header decode.
 *
 * <p>Change Intent: - Fix-forward remediation: Type strictness alignment. - EDITED (Batch 3B -
 * P3.2, VULN-21): Replaced the ML-DSA-only/blind-CAST-on-everything logic with alg-aware dispatch
 * (RS256/ES256 → Spring Security `JwtDecoder`; ML-DSA-87 → AegisPqcJwtService) to eliminate the
 * RS256 false-positive 100/BLOCK on every authenticated request.
 *
 * <p>Critical Dependencies: - Backend: AegisPqcJwtService, AegisBcsm, Spring Security
 * `org.springframework.security.oauth2.jwt.JwtDecoder`.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of JwtValidator. -
 * EDITED (Remediation): • Aligned method signature to `evaluate(HttpServletRequest, String)`. •
 * Converted raw strings to `SecurityDecision` enums. • Phase 2 Implementation. - EDITED (Batch 3B -
 * P3.2, VULN-21): • Injected `ObjectProvider<JwtDecoder> jwtDecoderProvider` (NOT direct JwtDecoder
 * — avoids circular dependency with SecurityConfig and avoids test-profile startup crash when no
 * Keycloak jwk-set-uri exists). • Header parse: split on `"\\."` (3 parts required),
 * Base64URL-decode `parts[0]` with length bound (≤ 8192), `ObjectMapper.readTree` (malformed →
 * 100/BLOCK). • Dispatch: `alg == "ML-DSA-87"` or `typ == "PQC-JWT"` →
 * `pqcJwtService.validateToken(token)` (invalid → 100/BLOCK). Else `alg` in {RS256, RS384, RS512,
 * ES256, ES384, ES512} → `jwtDecoderProvider.getIfAvailable()` → `decoder.decode(token)` (0/ALLOW
 * on success, 100/BLOCK on JwtException; null decoder → 0/ALLOW, defer to Spring Security). Else →
 * 100/BLOCK (none/unknown alg). • Behavior preserved: public routes (no Bearer) → 0/ALLOW;
 * malformed → 100/BLOCK.
 *
 * <p>- EDITED (Step 0 - Phase 3B Surgical Polish): • Added pre-guard `if (parts[0].length() >
 * 11_000) { return new ValidatorResult(100, SecurityDecision.BLOCK, "JWT_NODE"); }` immediately
 * before `Base64.getUrlDecoder().decode(parts[0])` — oversized JWT headers rejected prior to heap
 * allocation.
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import com.treishvaam.financeapi.security.aegis.crypto.AegisPqcJwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator implements AegisValidator {

    private final AegisPqcJwtService pqcJwtService;
    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtValidator(
            AegisPqcJwtService pqcJwtService, ObjectProvider<JwtDecoder> jwtDecoderProvider) {
        this.pqcJwtService = pqcJwtService;
        this.jwtDecoderProvider = jwtDecoderProvider;
    }

    @Override
    public ValidatorResult evaluate(HttpServletRequest request, String sessionId) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return new ValidatorResult(0, SecurityDecision.ALLOW, "JWT_NODE"); // Public route
        }

        String token = authHeader.substring(7);
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return new ValidatorResult(100, SecurityDecision.BLOCK, "JWT_NODE");
        }

        // Parse JWT header segment safely (Base64URL, length-bounded)
        JsonNode header;
        try {
            // Step 0: reject oversized JWT headers BEFORE Base64 allocation (heap DoS guard).
            if (parts[0].length() > 11_000) {
                return new ValidatorResult(100, SecurityDecision.BLOCK, "JWT_NODE");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            if (decoded.length > 8192) {
                return new ValidatorResult(100, SecurityDecision.BLOCK, "JWT_NODE");
            }
            header = objectMapper.readTree(decoded);
        } catch (IOException | IllegalArgumentException e) {
            return new ValidatorResult(100, SecurityDecision.BLOCK, "JWT_NODE");
        }

        String alg = header != null && header.has("alg") ? header.get("alg").asText() : null;
        String typ = header != null && header.has("typ") ? header.get("typ").asText() : null;

        // 1. ML-DSA-87 (PQC) or PQC-JWT typ → validate via PQC service
        if (alg != null && (alg.equals("ML-DSA-87") || "PQC-JWT".equals(typ))) {
            boolean isValid = pqcJwtService.validateToken(token);
            return new ValidatorResult(
                    isValid ? 0 : 100,
                    isValid ? SecurityDecision.ALLOW : SecurityDecision.BLOCK,
                    "JWT_NODE");
        }

        // 2. Standard Keycloak OIDC JWTs (RS256 etc.) → validate via Spring Security JwtDecoder
        if (alg != null
                && (alg.equals("RS256")
                        || alg.equals("RS384")
                        || alg.equals("RS512")
                        || alg.equals("ES256")
                        || alg.equals("ES384")
                        || alg.equals("ES512"))) {
            JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
            if (decoder != null) {
                try {
                    decoder.decode(token);
                    return new ValidatorResult(0, SecurityDecision.ALLOW, "JWT_NODE");
                } catch (JwtException e) {
                    return new ValidatorResult(100, SecurityDecision.BLOCK, "JWT_NODE");
                }
            }
            // No decoder bean available (e.g., test profile) — defer to Spring Security
            return new ValidatorResult(0, SecurityDecision.ALLOW, "JWT_NODE");
        }

        // 3. none / unknown alg — fail closed
        return new ValidatorResult(100, SecurityDecision.BLOCK, "JWT_NODE");
    }
}
