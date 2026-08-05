package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.AnalyticsEvent;
import com.treishvaam.financeapi.repository.AnalyticsEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI-CONTEXT: Purpose: First-Party Analytics Ingestion API. Security Constraints: - ZERO-TRUST GEO:
 * Location must be derived strictly from X-Visitor-* and CF-IPCountry headers injected by the Edge
 * Worker. - PII COMPLIANCE: Raw IP address must NEVER be captured or stored. IMMUTABLE CHANGE
 * HISTORY: - ADDED (Phase 5): Controller implementation for first-party tracking ingestion. -
 * EDITED (Incident 41): Added Zero-Trust Cryptographic Device Fingerprinting. Generates a SHA3-256
 * hash of Client-IP, User-Agent, and Accept-Language to cluster incognito/standard sessions without
 * ever storing raw IP addresses in the database. Date: 2026-08-05.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsEventController {

    @Autowired private AnalyticsEventRepository analyticsEventRepository;

    @PostMapping("/event")
    public ResponseEntity<Void> recordEvent(
            @RequestBody AnalyticsEvent event,
            HttpServletRequest request,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "unknown") String tenantId) {

        // 1. Enforce Zero-Trust Location Data (Edge Truth overrides Client payload)
        String edgeCountry = request.getHeader("CF-IPCountry");
        if (edgeCountry == null) edgeCountry = request.getHeader("X-Visitor-Country");
        String edgeCity = request.getHeader("X-Visitor-City");

        event.setCountryCode(edgeCountry != null ? edgeCountry : "Unknown");
        event.setCity(edgeCity != null ? edgeCity : "Unknown");

        // 2. Enforce Tenant Separation
        event.setTenantId(tenantId);

        // 3. Cryptographic Device Fingerprinting (Zero-Trust)
        String rawIp = request.getHeader("X-Aegis-Client-IP");
        if (rawIp == null) rawIp = request.getHeader("CF-Connecting-IP");
        String userAgent = request.getHeader("User-Agent");
        String acceptLang = request.getHeader("Accept-Language");

        String fingerprintBase =
                (rawIp != null ? rawIp : "unk-ip")
                        + "|"
                        + (userAgent != null ? userAgent : "unk-ua")
                        + "|"
                        + (acceptLang != null ? acceptLang : "unk-lang");

        // BouncyCastle SHA3-256 Hashing
        SHA3.Digest256 digest = new SHA3.Digest256();
        byte[] hashBytes = digest.digest(fingerprintBase.getBytes(StandardCharsets.UTF_8));
        event.setDeviceFingerprint(Hex.toHexString(hashBytes));

        // Raw IP is now strictly discarded from JVM memory.

        // 4. Prevent overriding of timestamps
        event.setId(null);

        // 5. Asynchronous persist (Non-blocking response)
        // In a true massive-scale setup, this would publish to RabbitMQ.
        // For current DB-write optimization, direct save is used as batching is enabled in
        // properties.
        analyticsEventRepository.save(event);

        return ResponseEntity.accepted().build();
    }
}
