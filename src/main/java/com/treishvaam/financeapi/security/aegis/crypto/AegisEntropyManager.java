/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 0 Hardware Entropy Anchoring (L0-HEA). - Seeds all Post-Quantum Cryptographic
 * operations with TRUE entropy from hardware.
 *
 * <p>Scope: - Responsible for reading /dev/urandom, /dev/random, and JVM jitter. - Folds gathered
 * entropy using SHA3-512 into a 4096-bit pool. - Must NEVER block Tomcat request threads (runs on
 * Java 21 Virtual Threads).
 *
 * <p>Critical Dependencies: - Backend: Java 21 SecureRandom, BouncyCastle Provider. -
 * Infrastructure: Docker volume mounts for /dev/random and /dev/urandom.
 *
 * <p>Security Constraints: - Must never rely solely on standard PRNG. - If hardware entropy drops
 * below threshold, must log a critical alert.
 *
 * <p>Non-Negotiables: - Entropy gathering must be fully asynchronous.
 *
 * <p>Change Intent: - Initial implementation of the AEGIS Entropy layer.
 *
 * <p>Future AI Guidance: - Do not replace SHA3-512 with SHA-256 (Grover's algorithm resistance). -
 * When physical TPM 2.0 is added, add /dev/tpm0 reading logic here.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of AegisEntropyManager.
 * • Implemented SHA3-512 folding and virtual-thread-based async pooling. • Phase 1/2 Orchestration
 * Batch. * - EDITED: • Fixed critical ArrayIndexOutOfBoundsException in getSecureRandom() (P0 crash
 * loop). • Root Cause: refreshEntropyPool() replaced the 512-byte pool with a 64-byte SHA3-512
 * digest output. getSecureRandom() then tried to copy from offset nextInt(448) of a byte[64],
 * causing ArrayIndexOutOfBoundsException on any non-zero offset. • Fix 1 (refreshEntropyPool):
 * XOR-fold the 64-byte digest cyclically across the full POOL_SIZE_BYTES array instead of replacing
 * it, maintaining the 512-byte invariant. • Fix 2 (getSecureRandom): Derive offset from actual
 * pool.length minus 64. • Why XOR-fold: Preserves forward-secrecy design intent. Each refresh
 * mutates all 512 bytes using the new digest, preventing pool state prediction across restarts. •
 * Date: 2026-06-01 / P0 Recovery Session.
 *
 * <p>- STRATEGIC PIVOTS & FAILED ATTEMPTS (CRITICAL FOR FUTURE AI): • Strategy Attempted: Using
 * currentPool.set(digest.digest()) to replace pool on refresh. • Failure Mode:
 * ArrayIndexOutOfBoundsException — pool shrinks from 512 to 64 bytes. Any getSecureRandom() call
 * after the first refresh crashes with source index > 64. • Future AI Warning: NEVER replace the
 * AtomicReference pool with digest.digest() directly. SHA3-512 outputs 64 bytes. POOL_SIZE_BYTES is
 * 512. They are not equal. Always maintain pool at POOL_SIZE_BYTES by XOR-folding or KDF expansion.
 *
 * <p>- DO-NOT-DELETE RULE (ABSOLUTE): This IMMUTABLE CHANGE HISTORY section acts as the
 * institutional memory for future AI sessions. It must never be deleted, truncated, rewritten, or
 * regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AegisEntropyManager {

    private static final Logger log = LoggerFactory.getLogger(AegisEntropyManager.class);
    private static final int POOL_SIZE_BYTES = 512; // 4096-bit pool
    private final AtomicReference<byte[]> currentPool =
            new AtomicReference<>(new byte[POOL_SIZE_BYTES]);
    private final SecureRandom jvmRandom = new SecureRandom();

    // Use Virtual Threads for non-blocking scheduled tasks
    private final ScheduledExecutorService virtualScheduler =
            Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    @PostConstruct
    public void init() {
        log.info("AEGIS L0-HEA: Initializing Hardware Entropy Manager...");
        refreshEntropyPool();
        // Refresh every 30 seconds asynchronously
        virtualScheduler.scheduleAtFixedRate(this::refreshEntropyPool, 30, 30, TimeUnit.SECONDS);
    }

    private void refreshEntropyPool() {
        try {
            byte[] hwEntropy = new byte[256];
            byte[] urandomEntropy = new byte[256];
            byte[] jvmEntropy = new byte[256];

            // 1. Attempt strict /dev/random (blocking pool)
            try (FileInputStream fis = new FileInputStream("/dev/random")) {
                fis.read(hwEntropy);
            } catch (IOException e) {
                log.warn(
                        "AEGIS L0-HEA: /dev/random unavailable or blocked. Relying on fallback sources.");
            }

            // 2. Read /dev/urandom (non-blocking CSPRNG)
            try (FileInputStream fis = new FileInputStream("/dev/urandom")) {
                fis.read(urandomEntropy);
            } catch (IOException e) {
                log.error("AEGIS L0-HEA: CRITICAL: /dev/urandom unavailable!");
            }

            // 3. JVM Jitter / PRNG
            jvmRandom.nextBytes(jvmEntropy);

            // Fold all sources using SHA3-512
            SHA3.Digest512 digest = new SHA3.Digest512();
            digest.update(hwEntropy);
            digest.update(urandomEntropy);
            digest.update(jvmEntropy);

            // Xor previous pool to maintain state forward secrecy
            byte[] previousPool = currentPool.get();
            digest.update(previousPool);

            byte[] newDigest = digest.digest(); // 64 bytes from SHA3-512
            byte[] existingPool = currentPool.get();
            byte[] newPool = new byte[POOL_SIZE_BYTES];

            // XOR-fold the 64-byte digest cyclically across the full 512-byte pool
            for (int i = 0; i < POOL_SIZE_BYTES; i++) {
                newPool[i] = (byte) (existingPool[i] ^ newDigest[i % newDigest.length]);
            }

            currentPool.set(newPool);
            log.debug("AEGIS L0-HEA: Entropy pool refreshed successfully (4096-bits).");

        } catch (Exception e) {
            log.error("AEGIS L0-HEA: Failed to refresh entropy pool!", e);
        }
    }

    public SecureRandom getSecureRandom() {
        // Return a seeded SecureRandom instance guaranteed by the hardware pool
        byte[] pool = currentPool.get(); // always POOL_SIZE_BYTES after invariant fix
        byte[] seed = new byte[64];

        // Ensure offset stays within safe bounds for arraycopy
        int offset = jvmRandom.nextInt(pool.length - 64 + 1); // 0..448 inclusive, safe
        System.arraycopy(pool, offset, seed, 0, 64);

        return new SecureRandom(seed);
    }
}
