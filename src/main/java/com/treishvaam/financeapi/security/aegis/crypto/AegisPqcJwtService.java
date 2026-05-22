/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 1 PQC Token Issuer (L1-PQCf). - Creates and validates Hybrid JWTs signed with
 * ML-DSA-87.
 *
 * <p>Scope: - Handles Base64Url encoding/decoding. - Signs payload strings using BouncyCastle
 * Signature instances. - Injects Tenant Context into the PQC layer.
 *
 * <p>Critical Dependencies: - AegisPqcKeyStore for the private/public keys.
 *
 * <p>Security Constraints: - Must explicitly verify the algorithm is ML-DSA before attempting
 * validation. - Signatures use pure Dilithium5, overriding weak RSA/ECDSA layers.
 *
 * <p>Non-Negotiables: - Do not use standard JJWT or Nimbus libraries here; they do not yet support
 * ML-DSA. Manual structural parsing is required.
 *
 * <p>Change Intent: - Initial implementation of quantum-safe token issuance.
 *
 * <p>Future AI Guidance: - Keep this manual token structure until Nimbus/Auth0 officially support
 * FIPS 204 ML-DSA.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Manual JWT construction for ML-DSA. •
 * Base64Url encoding for header, payload, and signature. • Phase 1/2 Orchestration Batch.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AegisPqcJwtService {

  private static final Logger log = LoggerFactory.getLogger(AegisPqcJwtService.class);
  private final AegisPqcKeyStore keyStore;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AegisPqcJwtService(AegisPqcKeyStore keyStore) {
    this.keyStore = keyStore;
  }

  public String issueHybridToken(String innerKeycloakJwt, String tenantId, String sessionId) {
    try {
      // 1. Construct Header
      Map<String, String> header = new HashMap<>();
      header.put("alg", "ML-DSA-87");
      header.put("typ", "PQC-JWT");
      String headerB64 = encodeB64(objectMapper.writeValueAsString(header));

      // 2. Construct Payload
      Map<String, Object> payload = new HashMap<>();
      payload.put("sub", innerKeycloakJwt); // Envelope the classical token
      payload.put("tenant", tenantId);
      payload.put("sid", sessionId);
      payload.put("iat", System.currentTimeMillis() / 1000);
      payload.put("exp", (System.currentTimeMillis() / 1000) + 3600); // 1 hour
      String payloadB64 = encodeB64(objectMapper.writeValueAsString(payload));

      // 3. Sign (Header + "." + Payload)
      String signingInput = headerB64 + "." + payloadB64;
      Signature sig = Signature.getInstance("Dilithium", BouncyCastlePQCProvider.PROVIDER_NAME);
      sig.initSign(keyStore.getCurrentKeyPair().getPrivate());
      sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
      byte[] signatureBytes = sig.sign();

      String signatureB64 = encodeB64(signatureBytes);

      return signingInput + "." + signatureB64;

    } catch (Exception e) {
      log.error("AEGIS L1-PQCf: Failed to issue PQC JWT", e);
      throw new RuntimeException("PQC Token Generation Failed", e);
    }
  }

  public boolean validateToken(String pqcToken) {
    try {
      String[] parts = pqcToken.split("\\.");
      if (parts.length != 3) return false;

      String signingInput = parts[0] + "." + parts[1];
      byte[] signatureBytes = decodeB64(parts[2]);

      Signature sig = Signature.getInstance("Dilithium", BouncyCastlePQCProvider.PROVIDER_NAME);
      sig.initVerify(keyStore.getCurrentKeyPair().getPublic());
      sig.update(signingInput.getBytes(StandardCharsets.UTF_8));

      return sig.verify(signatureBytes);
    } catch (Exception e) {
      log.warn("AEGIS L1-PQCf: PQC Token validation failed explicitly: {}", e.getMessage());
      return false;
    }
  }

  private String encodeB64(String data) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(data.getBytes(StandardCharsets.UTF_8));
  }

  private String encodeB64(byte[] data) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
  }

  private byte[] decodeB64(String data) {
    return Base64.getUrlDecoder().decode(data);
  }
}
