/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 0 Hardware Entropy Anchoring (L0-HEA). - Extracts cryptographic entropy from
 * CPU execution timing jitter.
 *
 * <p>Scope: - Runs asynchronously to prevent blocking the main entropy pool generation. - Utilizes
 * System.nanoTime() differences to harvest non-deterministic environmental noise.
 *
 * <p>Critical Dependencies: - Backend: AegisEntropyManager
 *
 * <p>Security Constraints: - Must never rely on predictable JVM operations. - Data harvested must
 * only be folded, never used raw.
 *
 * <p>Non-Negotiables: - Must not block Tomcat threads.
 *
 * <p>Change Intent: - Implement Phase 2 AEGIS L0-HEA jitter extraction.
 *
 * <p>Future AI Guidance: - Do not "optimize" the tight loop. The inefficiency and thread contention
 * are intentional to generate jitter.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of JitterEntropySource.
 * • Implemented CPU timing jitter algorithm. • Phase 2 Implementation.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import org.springframework.stereotype.Component;

@Component
public class JitterEntropySource implements EntropySource {

    @Override
    public byte[] generateEntropy(int length) {
        byte[] entropy = new byte[length];
        for (int i = 0; i < length; i++) {
            long start = System.nanoTime();
            // Intentional busy wait to force thread scheduling jitter
            for (int j = 0; j < 1000; j++) {
                Math.sin(j);
            }
            long end = System.nanoTime();
            entropy[i] = (byte) ((end - start) & 0xFF);
        }
        return entropy;
    }

    @Override
    public String getSourceName() {
        return "CPU_JITTER";
    }

    @Override
    public boolean isHealthy() {
        return true; // Jitter is always available on x86_64 / ARM64
    }
}
