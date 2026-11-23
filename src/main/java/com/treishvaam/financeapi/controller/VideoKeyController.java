/**
 * AI-CONTEXT: Purpose: Secure, internal-only endpoint to deliver AES-128 HLS DRM keys. Scope:
 * Serves raw byte streams for video decryption. Security Constraints: - MUST be intercepted by
 * AegisEdgeValidationFilter. - Keys are deterministic per videoId (HMAC-SHA256 derivation from
 * VIDEO_KEY_MASTER_SECRET or CONTENT_SIGNING_KEY) — NOT stored in plain text and NOT random
 * per-request. - Fail-closed: 503 when the master key is unconfigured. IMMUTABLE CHANGE HISTORY: -
 * ADDED: Phase 2 HLS Video Key Delivery endpoint. - EDITED (Batch 2B - P2.5, VULN-17): Removed
 * random per-request key generation; keys are now derived deterministically per videoId via
 * HMAC-SHA256 (16-byte truncation) so key requests are repeatable across replicas and the
 * transcoder's raw video stream maps to a predictable, verifiable key; added {@code videoId} format
 * validation (^[0-9]{1,18}$) → 400; fail-closed 503 when VIDEO_KEY_MASTER_SECRET /
 * CONTENT_SIGNING_KEY is blank.
 */
package com.treishvaam.financeapi.controller;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/v1/video/internal/key")
public class VideoKeyController {

    @Value("${VIDEO_KEY_MASTER_SECRET:${CONTENT_SIGNING_KEY:}}")
    private String masterSecret;

    @GetMapping("/{videoId}")
    public ResponseEntity<byte[]> getVideoKey(@PathVariable String videoId) {
        // Validate videoId format: numeric, 1-18 digits (matches transcoder.py)
        if (videoId == null || !videoId.matches("^[0-9]{1,18}$")) {
            return ResponseEntity.badRequest().build();
        }

        // Fail closed when no signing secret is configured
        if (masterSecret == null || masterSecret.isBlank()) {
            log.error(
                    "VIDEO_KEY_MASTER_SECRET/CONTENT_SIGNING_KEY not configured — serving 503 for videoId={}",
                    videoId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        // Deterministic 16-byte AES-128 key: HMAC-SHA256 over the videoId context
        byte[] aesKey;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(masterSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] fullHmac =
                    mac.doFinal(
                            ("treishvaam-hls-aes128-v1:" + videoId)
                                    .getBytes(StandardCharsets.UTF_8));
            aesKey = Arrays.copyOf(fullHmac, 16);
        } catch (Exception e) {
            log.error("Failed to derive HLS AES-128 key for videoId={}", videoId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(aesKey.length);
        // Explicitly block downstream caching on the backend side; edge worker controls TTL
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(aesKey, headers, HttpStatus.OK);
    }
}
