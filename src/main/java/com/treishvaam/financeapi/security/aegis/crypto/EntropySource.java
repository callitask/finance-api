/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Defines the strict contract for hardware and software entropy sources feeding into
 * the 4096-bit SHA3-512 folded pool.
 *
 * <p>Scope: - Responsible for standardizing entropy generation across different OS/Hardware
 * capabilities. - Must never block the main thread; implementations must be called asynchronously.
 *
 * <p>Critical Dependencies: - Backend: AegisEntropyManager requires this interface to aggregate
 * chaotic data.
 *
 * <p>Security Constraints: - Must never expose the raw seed state to any logging or observability
 * platform.
 *
 * <p>Non-Negotiables: - Must gracefully degrade to software entropy if hardware devices (e.g.,
 * TPM0) are unavailable.
 *
 * <p>Change Intent: - Fix-forward remediation: Creating missing interface to unblock Phase 2
 * compilation.
 *
 * <p>Future AI Guidance: - Add Quantum Random Number Generator (QRNG) API implementations here in
 * Phase 7.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Interface created. • Required for
 * hardware-anchored ML-DSA key generation. • Phase 2 Remediation Batch.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

public interface EntropySource {
    /**
     * Extracts chaotic bits from the underlying system.
     *
     * @param length Number of bytes required.
     * @return Cryptographically secure random bytes.
     */
    byte[] generateEntropy(int length);

    /**
     * @return The identifier of the entropy provider (e.g., "DEV_URANDOM", "TPM_HW").
     */
    String getSourceName();

    /**
     * Verifies if the source is currently providing high-quality entropy.
     *
     * @return true if healthy.
     */
    boolean isHealthy();
}
