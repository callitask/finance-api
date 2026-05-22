/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 1 Post-Quantum Cryptographic Foundation (L1-PQCf) Key Store. - Generates and
 * holds ML-DSA-87 (Dilithium5) keypairs.
 *
 * <p>Scope: - Registers BouncyCastle and BCPQC providers dynamically to avoid JVM conflicts. -
 * Generates NIST Level 5 Post-Quantum keys.
 *
 * <p>Critical Dependencies: - Backend: org.bouncycastle:bcprov-jdk18on,
 * org.bouncycastle:bcpkix-jdk18on - L0-HEA: AegisEntropyManager
 *
 * <p>Security Constraints: - Private keys MUST remain in memory and never be serialized to disk
 * logs.
 *
 * <p>Non-Negotiables: - Must strictly use ML-DSA / Dilithium. No RSA/ECC fallbacks for AEGIS native
 * tokens.
 *
 * <p>Change Intent: - Initial PQC keygen implementation.
 *
 * <p>Future AI Guidance: - When HashiCorp Vault is integrated, modify this class to pull the
 * private key from Vault instead of generating it in memory on boot.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • BouncyCastle PQC provider registration. •
 * ML-DSA key generation mapped to NIST Level 5. • Phase 1/2 Orchestration Batch.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import jakarta.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AegisPqcKeyStore {

  private static final Logger log = LoggerFactory.getLogger(AegisPqcKeyStore.class);
  private final AegisEntropyManager entropyManager;
  private KeyPair currentPqcKeyPair;

  public AegisPqcKeyStore(AegisEntropyManager entropyManager) {
    this.entropyManager = entropyManager;
  }

  @PostConstruct
  public void init() {
    log.info("AEGIS L1-PQCf: Registering BouncyCastle Providers...");
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
    if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastlePQCProvider());
    }
    generateNewKeypair();
  }

  public void generateNewKeypair() {
    try {
      log.info(
          "AEGIS L1-PQCf: Generating ML-DSA-87 (Dilithium5) KeyPair. This may take a moment...");
      KeyPairGenerator kpg =
          KeyPairGenerator.getInstance("Dilithium", BouncyCastlePQCProvider.PROVIDER_NAME);

      // Dilithium5 is equivalent to ML-DSA-87 (NIST Level 5)
      kpg.initialize(DilithiumParameterSpec.dilithium5, entropyManager.getSecureRandom());
      this.currentPqcKeyPair = kpg.generateKeyPair();
      log.info("AEGIS L1-PQCf: ML-DSA-87 KeyPair generated successfully.");
    } catch (Exception e) {
      log.error("AEGIS L1-PQCf: CRITICAL FAILURE generating PQC keys!", e);
      throw new RuntimeException("Failed to generate PQC keys", e);
    }
  }

  public KeyPair getCurrentKeyPair() {
    return currentPqcKeyPair;
  }
}
