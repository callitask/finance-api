/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implements the Java gRPC client for Zero-Knowledge Proof (L3-ZKA) verification.
 *
 * <p>Scope: - Establishes a secure internal channel to the `aegis-zkp-service` Go module.
 *
 * <p>Critical Dependencies: - Protobuf auto-generated classes (ZkpValidatorGrpc, ZkpRequest,
 * ZkpResponse). - Resilience4j: Provides the Circuit Breaker to prevent connection hanging.
 *
 * <p>Security Constraints: - Channel uses plaintext internally strictly because it operates on an
 * isolated Docker network. - MUST fail closed in the event of microservice downtime to prevent ZKP
 * bypasses. - VULN-04d: publicKey is always set on ZkpRequest (previously never set, so the Go side
 * could not validate any real proof — any non-empty check rejected ALL Java calls).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: Phase 5 (L3-ZKA). • Built the client to
 * replace dummy stubs and interface with the live gnark lattice circuit.
 *
 * <p>- EDITED (Batch 3A - P3.1, VULN-04d): • Added `@Value("${AEGIS_ADMIN_ZKP_PUBLIC_KEY:}")` field
 * `configuredAdminPublicKey`. • Added 4-arg `verifyProof(adminId, publicKey, challengeId,
 * proofDataHex)` — calls `.setPublicKey(ByteString.copyFromUtf8(publicKey))` on
 * `ZkpRequest.newBuilder()`; fail-closed on blank publicKey; hex-decodes `proofDataHex` via
 * `HexFormat.of().parseHex(...)` (rejects malformed hex strings before gRPC; accepts even-length
 * hex). • Kept the 3-arg `verifyProof(adminId, challengeId, proofDataHex)` overload delegating to
 * the 4-arg with the configured default public key (all existing callers unchanged). • Behavior
 * preserved: channel config, `@CircuitBreaker(name = "zkpService")` + fail-closed fallback, error
 * logging, `PreDestroy` shutdown.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import com.google.protobuf.ByteString;
import com.treishvaam.financeapi.security.aegis.zkp.ZkpRequest;
import com.treishvaam.financeapi.security.aegis.zkp.ZkpResponse;
import com.treishvaam.financeapi.security.aegis.zkp.ZkpValidatorGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AegisZkpServiceClient {

    private final ManagedChannel channel;
    private final ZkpValidatorGrpc.ZkpValidatorBlockingStub blockingStub;

    // Configured admin public key hash (VULN-04d). When blank, callers must supply a public key.
    @Value("${AEGIS_ADMIN_ZKP_PUBLIC_KEY:}")
    private String configuredAdminPublicKey;

    public AegisZkpServiceClient(
            @Value("${aegis.zkp.target:aegis-zkp-service:9090}") String target) {
        this.channel =
                ManagedChannelBuilder.forTarget(target)
                        .usePlaintext() // Internal isolated Docker network only
                        .build();
        this.blockingStub = ZkpValidatorGrpc.newBlockingStub(channel);
        log.info("AEGIS L3-ZKA: Initialized secure gRPC channel to {}", target);
    }

    /**
     * 3-arg overload kept for compatibility — delegates to {@link #verifyProof(String, String,
     * String, String)} with the configured default public key.
     */
    @CircuitBreaker(name = "zkpService", fallbackMethod = "verifyFallback")
    public boolean verifyProof(String adminId, String challengeId, String proofDataHex) {
        return verifyProof(adminId, configuredAdminPublicKey, challengeId, proofDataHex);
    }

    @CircuitBreaker(name = "zkpService", fallbackMethod = "verifyFallback")
    public boolean verifyProof(
            String adminId, String publicKey, String challengeId, String proofDataHex) {
        // Fail closed: publicKey must be present and non-blank
        if (publicKey == null || publicKey.isBlank()) {
            log.warn("AEGIS L3-ZKA: No public key configured/supplied — rejecting (fail closed).");
            return false;
        }
        if (challengeId == null || challengeId.isBlank()) {
            log.warn("AEGIS L3-ZKA: Missing challenge ID — rejecting.");
            return false;
        }

        // Decode proofDataHex: accept valid even-length hex; reject anything else so malformed
        // proof strings fail closed BEFORE hitting gRPC.
        byte[] proofBytes;
        if (proofDataHex == null || proofDataHex.isBlank()) {
            log.warn("AEGIS L3-ZKA: Missing proof payload — rejecting.");
            return false;
        }
        try {
            proofBytes = HexFormat.of().parseHex(proofDataHex.trim());
        } catch (IllegalArgumentException e) {
            log.warn("AEGIS L3-ZKA: Invalid hex proof payload rejected: {}", e.getMessage());
            return false;
        }
        if (proofBytes.length == 0) {
            log.warn("AEGIS L3-ZKA: Empty proof payload — rejecting.");
            return false;
        }

        log.debug(
                "AEGIS L3-ZKA: Transmitting mathematically verifiable identity proof for challenge {}",
                challengeId);

        ZkpRequest request =
                ZkpRequest.newBuilder()
                        .setSessionId(adminId)
                        .setPublicKey(ByteString.copyFromUtf8(publicKey))
                        .setChallengeString(ByteString.copyFromUtf8(challengeId))
                        .setProofData(ByteString.copyFrom(proofBytes))
                        .build();

        ZkpResponse response = blockingStub.verifyProof(request);

        if (!response.getIsValid()) {
            log.warn(
                    "AEGIS L3-ZKA: Proof mathematically REJECTED. gnark trace: {}",
                    response.getErrorMessage());
        }

        return response.getIsValid();
    }

    public boolean verifyFallback(
            String adminId, String challengeId, String proofDataHex, Throwable t) {
        log.error(
                "AEGIS L3-ZKA: gRPC Service Unreachable! Circuit Breaker triggered for Admin: {}. FAILING CLOSED.",
                adminId,
                t);
        return false; // Absolute Zero-Trust: If service is unreachable, access is denied.
    }

    public boolean verifyFallback(
            String adminId,
            String publicKey,
            String challengeId,
            String proofDataHex,
            Throwable t) {
        log.error(
                "AEGIS L3-ZKA: gRPC Service Unreachable! Circuit Breaker triggered. FAILING CLOSED.",
                t);
        return false;
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}
