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
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PowChallengeIssuer {

  private final SecureRandom secureRandom = new SecureRandom();
  private static final int LEADING_ZEROS_REQUIRED = 24; // 2^24 hashes required

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
        String.format("v1:%d:%d:%s", LEADING_ZEROS_REQUIRED, Instant.now().toEpochMilli(), nonce);

    response.setStatus(HttpServletResponse.SC_ACCEPTED);
    response.setHeader("X-AEGIS-POW-Challenge", challengePayload);
    response.setContentType("application/json");
    response
        .getWriter()
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
      String input = challenge + proof;
      SHA3.Digest256 digest = new SHA3.Digest256();
      byte[] hash = digest.digest(input.getBytes());
      String hexHash = Hex.toHexString(hash);

      // Calculate required zeros in hex (24 bits = 6 hex characters)
      int requiredHexZeros = LEADING_ZEROS_REQUIRED / 4;
      String requiredPrefix = "0".repeat(requiredHexZeros);

      return hexHash.startsWith(requiredPrefix);
    } catch (Exception e) {
      log.warn("AEGIS L7-CMCS: Malformed PoW validation attempt", e);
      return false;
    }
  }
}
