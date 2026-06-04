package com.treishvaam.financeapi.security.aegis;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Cryptographic Origin Enforcement. Ensures all traffic hitting the Tomcat backend
 * has legitimately passed through the Cloudflare Edge Worker and has not been directly routed via
 * IP scanning.
 *
 * <p>Scope: - Validates the X-Aegis-Edge-Signature using HMAC-SHA-512. - Enforces a 300-second TTL
 * on the X-Aegis-Edge-Timestamp to prevent replay attacks. - Exempts internal infrastructure
 * traffic (127.0.0.1, Prometheus/Grafana health checks).
 *
 * <p>Critical Dependencies: - Backend: application.properties (aegis.edge.secret) -
 * Frontend/Worker: Cloudflare Worker appending the cryptographic headers. - Cryptography:
 * BouncyCastleProvider (Phase 1 foundation).
 *
 * <p>Security Constraints: - Constant-time string comparison MUST be used to prevent timing
 * attacks. - AEGIS_EDGE_SECRET MUST NEVER be hardcoded. It is injected via .env. - BouncyCastle
 * must be used over standard JCE to maintain the PQC dependency chain.
 *
 * <p>Non-Negotiables: - If the signature is invalid, missing, or expired, instantly return 403
 * Forbidden. No routing occurs.
 *
 * <p>Change Intent: - Phase 6 Zero-Trust execution to eliminate direct-IP bypass vulnerabilities.
 *
 * <p>Future AI Guidance: - Do NOT remove the internal IP bypass logic, or the CI/CD and
 * observability stack will go offline. - Do NOT refactor the constant-time MessageDigest.isEqual()
 * check into a standard String.equals().
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of
 * AegisEdgeValidationFilter. • Enforces HMAC-SHA-512 edge signature validation and 300s TTL. •
 * Added internal network and actuator bypass logic. • Date/Phase: 2026-05-24 / Phase 6 (Zero-Trust
 * Loop Closure)
 *
 * <p>- EDITED: • Updated extractClientIp() to prioritize CF-Connecting-IP over X-Real-IP. • Why:
 * The Cloudflare Edge Worker generates the HMAC-SHA-512 signature using CF-Connecting-IP. Nginx/Lua
 * X-Real-IP extraction introduces IPv6 formatting drift, breaking the constant-time mathematical
 * verification and causing a flood of 403 Forbidden errors.
 *
 * <p>- EDITED (Cloudflare Spoofing Prevention): • Updated extractClientIp() to prioritize
 * X-Aegis-Client-IP as the absolute source of truth. • Why: Tomcat must perform HMAC verification
 * using the exact IP mathematically sealed into the signature by the Cloudflare Worker.
 * Cloudflare's core network enforces spoofing protection and overwrites CF-Connecting-IP on
 * outbound fetches from the Worker, causing IPv6 network drift and false 403s. The custom header
 * creates an immutable bridge.
 *
 * <p>- EDITED (Cryptographic Delimiter Bug Fix): • Changed payloadToSign reconstruction to include
 * colon ":" delimiters. • Why: The Cloudflare Worker seals the HMAC payload using colons
 * `path:timestamp:ip`. The backend was concatenating them directly `pathtimestampip`, meaning the
 * cryptographic math would permanently fail. Adding the colons perfectly aligns the verification
 * string with the Edge signature, resolving the 403 Forbidden loop and stopping the AEGIS Deception
 * Engine from falsely trapping legitimate traffic.
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Executes before Spring Security chain
public class AegisEdgeValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AegisEdgeValidationFilter.class);

    private static final String SIGNATURE_HEADER = "X-Aegis-Edge-Signature";
    private static final String TIMESTAMP_HEADER = "X-Aegis-Edge-Timestamp";
    private static final long MAX_TIME_DRIFT_SECONDS = 300; // 5 minutes

    @Value("${aegis.edge.secret}")
    private String edgeSecret;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Fail-Safe Internal Bypass
        if (isInternalOrHealthCheckRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract Headers
        String providedSignature = request.getHeader(SIGNATURE_HEADER);
        String providedTimestamp = request.getHeader(TIMESTAMP_HEADER);

        if (providedSignature == null || providedTimestamp == null) {
            logger.warn(
                    "AEGIS BLOCK: Missing Edge Validation Headers from IP: {}",
                    request.getRemoteAddr());
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Origin Access Denied - Direct IP Access Blocked");
            return;
        }

        // 3. Prevent Replay Attacks (Time Drift Validation)
        try {
            long timestampMillis = Long.parseLong(providedTimestamp);
            long currentMillis = Instant.now().toEpochMilli();
            if (Math.abs(currentMillis - timestampMillis) > (MAX_TIME_DRIFT_SECONDS * 1000)) {
                logger.warn(
                        "AEGIS BLOCK: Expired Edge Timestamp from IP: {}", request.getRemoteAddr());
                response.sendError(
                        HttpServletResponse.SC_FORBIDDEN, "Origin Access Denied - Payload Expired");
                return;
            }
        } catch (NumberFormatException e) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN, "Origin Access Denied - Malformed Timestamp");
            return;
        }

        // 4. Reconstruct and Validate HMAC-SHA-512 Signature
        String clientIp = extractClientIp(request);
        String uri = request.getRequestURI();

        // CRITICAL FIX: Include colon delimiters to perfectly match the Cloudflare Worker's
        // signature payload format
        String payloadToSign = uri + ":" + providedTimestamp + ":" + clientIp;

        if (!isSignatureValid(payloadToSign, providedSignature)) {
            logger.error("AEGIS BLOCK: Cryptographic Signature Mismatch from IP: {}", clientIp);
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Origin Access Denied - Invalid Cryptographic Signature");
            return;
        }

        // Signature verified. Proceed.
        filterChain.doFilter(request, response);
    }

    private boolean isSignatureValid(String payload, String providedSignature) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512", BouncyCastleProvider.PROVIDER_NAME);
            SecretKeySpec secretKey =
                    new SecretKeySpec(edgeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);

            byte[] hashBytes = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = HexFormat.of().formatHex(hashBytes);

            // Constant-time string comparison to prevent timing side-channel attacks
            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    providedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error(
                    "AEGIS SYSTEM ERROR: Cryptographic subsystem failure during HMAC validation",
                    e);
            return false;
        }
    }

    private boolean isInternalOrHealthCheckRequest(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String path = request.getRequestURI();

        boolean isLocalIp = "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
        boolean isInternalNetwork =
                ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.");
        boolean isActuator = path.startsWith("/actuator") || path.startsWith("/health");

        return (isLocalIp || isInternalNetwork) && isActuator;
    }

    private String extractClientIp(HttpServletRequest request) {
        // AI-CONTEXT: Prioritize X-Aegis-Client-IP injected by the Edge Worker to survive CF
        // outbound IP spoofing overwrites.
        String aegisClientIp = request.getHeader("X-Aegis-Client-IP");
        if (aegisClientIp != null && !aegisClientIp.isEmpty()) {
            return aegisClientIp;
        }

        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp;
        }

        // Must match OpenResty $real_client_ip logic from Phase 1
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
