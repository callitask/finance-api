/**
 * AI-CONTEXT: Purpose: Secure, internal-only endpoint to deliver AES-128 HLS DRM keys. Scope:
 * Serves raw byte streams for video decryption. Security Constraints: - MUST be intercepted by
 * AegisEdgeValidationFilter. - Keys are dynamically retrieved/generated from MinIO or a secure
 * vault, NOT stored in plain text. IMMUTABLE CHANGE HISTORY: - ADDED: Phase 2 HLS Video Key
 * Delivery endpoint.
 */
package com.treishvaam.financeapi.controller;

import java.security.SecureRandom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/video/internal/key")
public class VideoKeyController {

    @GetMapping("/{videoId}")
    public ResponseEntity<byte[]> getVideoKey(@PathVariable String videoId) {
        // In a full implementation, you fetch the specific 16-byte key for this videoId
        // from MinIO or your secure Vault.
        // For this architectural scaffolding, we simulate a secure 16-byte AES key payload.
        byte[] aesKey = new byte[16];
        new SecureRandom().nextBytes(aesKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(aesKey.length);
        // Explicitly block downstream caching on the backend side; edge worker controls TTL
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(aesKey, headers, HttpStatus.OK);
    }
}
