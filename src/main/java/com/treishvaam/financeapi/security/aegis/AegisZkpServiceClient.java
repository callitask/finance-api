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
 * bypasses.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: Phase 5 (L3-ZKA). • Built the client to
 * replace dummy stubs and interface with the live gnark lattice circuit.
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AegisZkpServiceClient {

    private final ManagedChannel channel;
    private final ZkpValidatorGrpc.ZkpValidatorBlockingStub blockingStub;

    public AegisZkpServiceClient(
            @Value("${aegis.zkp.target:aegis-zkp-service:9090}") String target) {
        this.channel =
                ManagedChannelBuilder.forTarget(target)
                        .usePlaintext() // Internal isolated Docker network only
                        .build();
        this.blockingStub = ZkpValidatorGrpc.newBlockingStub(channel);
        log.info("AEGIS L3-ZKA: Initialized secure gRPC channel to {}", target);
    }

    @CircuitBreaker(name = "zkpService", fallbackMethod = "verifyFallback")
    public boolean verifyProof(String adminId, String challengeId, String proofDataHex) {
        log.debug(
                "AEGIS L3-ZKA: Transmitting mathematically verifiable identity proof for challenge {}",
                challengeId);

        ZkpRequest request =
                ZkpRequest.newBuilder()
                        .setSessionId(adminId)
                        .setChallengeString(ByteString.copyFromUtf8(challengeId))
                        .setProofData(ByteString.copyFromUtf8(proofDataHex))
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

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}
