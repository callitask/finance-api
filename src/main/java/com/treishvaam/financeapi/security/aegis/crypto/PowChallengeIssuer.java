/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implements the CPU-exhausting SHA3-256 Hash Burn (L7-CMCS Counter-Measure 2). -
 * Forces credential-stuffing botnets to solve cryptographic puzzles, rendering automated attacks
 * economically unviable.
 *
 * <p>Scope: - Issues challenges via `X-AEGIS-POW-Challenge` header and validates incoming proofs. -
 * Does not perform the hash generation for the client, only issues the nonce and checks the proof.
 *
 * <p>Critical Dependencies: - Crypto: Uses BouncyCastle SHA3-256 implementations for post-quantum
 * safe hashing.
 *
 * <p>Security Constraints: - Nonces must be cryptographically secure and short-lived. - Validation
 * must execute in bounded time to prevent algorithmic complexity DoS against the backend itself.
 *
 * <p>Non-Negotiables: - Must return HTTP 202 Accepted initially to deceive automated tools into
 * processing the headers instead of failing out.
 *
 * <p>Change Intent: - Phase 4: Implemented PoW challenge issuer to fulfill L7-CMCS requirements.
 *
 * <p>Future AI Guidance: - The difficulty level (leading zeros) can be adjusted dynamically based
 * on server load or BIE bot-confidence score.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Implemented `PowChallengeIssuer`
 * utilizing BouncyCastle SHA3. • Why it was added: Provides active resistance to GPU-cluster brute
 * force and credential stuffing. • Date: 2026-05-22
 *
 * <p>- EDITED (Batch 3A - P3.3, VULN-22a): • Injected `RedisTemplate<String, String>` via
 * constructor for replay prevention. • Hardened `validateProof(...)`: (a) parse
 * `challenge.split(":")` — require exactly 4 parts, `"v1"` version; (b) freshness —
 * `Long.parseLong(parts[2])` with `Math.abs(Instant.now().toEpochMilli() - issuedMs) > 120_000L`
 * (2-minute TTL); (c) blank nonce rejection; (d) after confirming SHA3-256 `hexHash.startsWith(
 * requiredPrefix)`, atomic single-use replay prevention via
 * `redisTemplate.opsForValue().setIfAbsent("aegis:pow:used:" + parts[3], "1",
 * Duration.ofMinutes(3))`, returning `Boolean.TRUE.equals(firstUse)`; (e) any parse/Redis exception
 * → `false`.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PowChallengeIssuer {

    private final SecureRandom secureRandom = new SecureRandom();
    private final RedisTemplate<String, String> redisTemplate;
    private static final int LEADING_ZEROS_REQUIRED = 24; // 2^24 hashes required
    private static final long CHALLENGE_TTL_MS = 120_000L; // 2-minute freshness window (VULN-22a)

    public PowChallengeIssuer(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Issues a PoW challenge to the client. Returns HTTP 202 Accepted to prevent early client-side
     * connection drops from standard 4xx/5xx responses.
     */
    public void issueChallenge(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        byte[] nonceBytes = new byte[32];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);

        // Payload format: version:difficulty:timestamp:nonce
        String challengePayload =
                String.format(
                        "v1:%d:%d:%s", LEADING_ZEROS_REQUIRED, Instant.now().toEpochMilli(), nonce);

        response.setStatus(HttpServletResponse.SC_ACCEPTED);
        response.setHeader("X-AEGIS-POW-Challenge", challengePayload);
        response.setContentType("application/json");
        response.getWriter()
                .write(
                        "{\"status\":\"processing\", \"instruction\":\"Solve X-AEGIS-POW-Challenge to proceed\"}");
        response.getWriter().flush();
    }

    /**
     * Validates a returned Proof-of-Work. proof = client-side counter/string that when appended to
     * the challenge and hashed, satisfies the condition.
     */
    public boolean validateProof(String challenge, String proof) {
        if (challenge == null || proof == null || challenge.isBlank()) {
            return false;
        }

        try {
            // (a) Parse: require v1:difficulty:timestamp:nonce structure (VULN-22a)
            String[] parts = challenge.split(":");
            if (parts.length != 4 || !"v1".equals(parts[0])) {
                log.warn("AEGIS L7-CMCS: Malformed PoW challenge structure rejected");
                return false;
            }
            if (Integer.parseInt(parts[1]) != LEADING_ZEROS_REQUIRED) {
                log.warn("AEGIS L7-CMCS: PoW difficulty mismatch rejected");
                return false;
            }
            // (b) Timestamp freshness: 2-minute TTL
            long issuedMs;
            try {
                issuedMs = Long.parseLong(parts[2]);
            } catch (NumberFormatException nfe) {
                log.warn("AEGIS L7-CMCS: Unparseable PoW challenge timestamp rejected");
                return false;
            }
            if (Math.abs(Instant.now().toEpochMilli() - issuedMs) > CHALLENGE_TTL_MS) {
                log.warn("AEGIS L7-CMCS: Stale PoW challenge rejected (2-min TTL)");
                return false;
            }
            if (parts[3].isBlank()) {
                log.warn("AEGIS L7-CMCS: Blank PoW challenge nonce rejected");
                return false;
            }

            String input = challenge + proof;
            SHA3.Digest256 digest = new SHA3.Digest256();
            byte[] hash = digest.digest(input.getBytes());
            String hexHash = Hex.toHexString(hash);

            // Calculate required zeros in hex (24 bits = 6 hex characters)
            int requiredHexZeros = LEADING_ZEROS_REQUIRED / 4;
            String requiredPrefix = "0".repeat(requiredHexZeros);

            if (!hexHash.startsWith(requiredPrefix)) {
                return false;
            }

            // (c) Atomic single-use replay prevention (fail closed on Redis errors)
            Boolean firstUse =
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent("aegis:pow:used:" + parts[3], "1", Duration.ofMinutes(3));
            return Boolean.TRUE.equals(firstUse);
        } catch (Exception e) {
            log.warn("AEGIS L7-CMCS: Malformed PoW validation attempt", e);
            return false;
        }
    }
}
