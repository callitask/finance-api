/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 0 Hardware Entropy Anchoring (L0-HEA). - Extracts cryptographic entropy from
 * the OS kernel non-blocking CSPRNG (/dev/urandom).
 *
 * <p>Scope: - Safely reads from the Unix filesystem.
 *
 * <p>Critical Dependencies: - Backend: AegisEntropyManager - Infrastructure: Unix-based host or
 * Docker environment.
 *
 * <p>Security Constraints: - Fails gracefully if the host is Windows or the mount is denied.
 *
 * <p>Non-Negotiables: - Read operations must use try-with-resources to prevent file descriptor
 * leaks.
 *
 * <p>Change Intent: - Implement Phase 2 AEGIS L0-HEA OS kernel entropy extraction.
 *
 * <p>Future AI Guidance: - Do not change this to /dev/random to prevent blocking the entropy
 * scheduler.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of UrandomEntropySource.
 * • Implemented /dev/urandom file stream reader. • Phase 2 Implementation.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import java.io.FileInputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UrandomEntropySource implements EntropySource {

    private static final Logger log = LoggerFactory.getLogger(UrandomEntropySource.class);
    private static final String URANDOM_PATH = "/dev/urandom";

    @Override
    public byte[] generateEntropy(int length) {
        byte[] entropy = new byte[length];
        try (FileInputStream fis = new FileInputStream(URANDOM_PATH)) {
            int read = fis.read(entropy);
            if (read < length) {
                log.warn(
                        "AEGIS L0-HEA: Partial read from urandom. Expected {}, got {}",
                        length,
                        read);
            }
        } catch (IOException e) {
            log.error("AEGIS L0-HEA: Failed to read from {}", URANDOM_PATH, e);
        }
        return entropy;
    }

    @Override
    public String getSourceName() {
        return "OS_URANDOM";
    }

    @Override
    public boolean isHealthy() {
        try (FileInputStream fis = new FileInputStream(URANDOM_PATH)) {
            return fis.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }
}
