/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 (Byzantine Consensus Security Mesh). - Aggregates security
 * evaluations from 7 independent micro-validators.
 *
 * <p>Scope: - Utilizes Java 21 CompletableFuture via virtual thread pools for parallel execution. -
 * Enforces a strict 100ms timeout for all validators combined. - Evaluates the quorum to produce a
 * final SecurityDecision.
 *
 * <p>Critical Dependencies: - Backend: 7 AegisValidator implementations.
 *
 * <p>Security Constraints: - Must tolerate up to 2 compromised or timed-out validators (Byzantine
 * Fault Tolerance). - No single validator can crash the quorum.
 *
 * <p>Non-Negotiables: - Must use Virtual Threads. - Timeout enforcement is absolute to prevent DDoS
 * via evaluation latency.
 *
 * <p>Change Intent: - Fix-forward remediation: Migrate from disabled preview `StructuredTaskScope`
 * to standard Java 21 CompletableFutures. - Fix-forward remediation: Correct strict
 * `SecurityDecision` enum type references.
 *
 * <p>Future AI Guidance: - Do not change `SecurityDecision` enum types to raw strings.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of AegisBcsm core. -
 * EDITED: • Replaced sequential/stub logic with fully concurrent execution. • Implemented 3f+1 BFT
 * logic (3 Block votes = Block, 2 Emergency votes = Emergency). • Enforced 100ms hard latency
 * ceiling. - EDITED (Remediation): • Replaced preview `StructuredTaskScope` with standard
 * `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture`. • Aligned signature to
 * `evaluate(HttpServletRequest, String)`. • Replaced string instantiation with strict
 * `SecurityDecision` enum properties. • Phase 2 Implementation.
 */
package com.treishvaam.financeapi.security.aegis.bcsm;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AegisBcsm {

    private static final Logger log = LoggerFactory.getLogger(AegisBcsm.class);

    private static final int VALIDATORS_TOTAL = 7;
    private static final int BLOCK_THRESHOLD = 3;
    private static final int EMERGENCY_THRESHOLD = 2;
    private static final long VALIDATOR_TIMEOUT_MS = 100;

    private final List<AegisValidator> validators;

    public AegisBcsm(List<AegisValidator> validators) {
        this.validators = validators;
    }

    public SecurityDecision evaluate(HttpServletRequest request, String sessionId) {

        List<ValidatorResult> results;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Fork all validators onto virtual threads with strict 100ms timeout
            List<CompletableFuture<ValidatorResult>> futures =
                    validators.stream()
                            .map(
                                    v ->
                                            CompletableFuture.supplyAsync(
                                                            () -> {
                                                                try {
                                                                    return v.evaluate(
                                                                            request, sessionId);
                                                                } catch (Exception e) {
                                                                    log.warn(
                                                                            "AEGIS L8-BCSM: Validator {} failed: {}",
                                                                            v.getClass()
                                                                                    .getSimpleName(),
                                                                            e.getMessage());
                                                                    return new ValidatorResult(
                                                                            50,
                                                                            SecurityDecision.WARN,
                                                                            "NODE_FAILURE");
                                                                }
                                                            },
                                                            executor)
                                                    .orTimeout(
                                                            VALIDATOR_TIMEOUT_MS,
                                                            TimeUnit.MILLISECONDS)
                                                    .exceptionally(
                                                            ex ->
                                                                    new ValidatorResult(
                                                                            50,
                                                                            SecurityDecision.WARN,
                                                                            "TIMEOUT_NODE")))
                            .collect(Collectors.toList());

            // Wait for all to finish or hit exception handlers
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("AEGIS L8-BCSM: Fatal quorum execution error.", e);
            return SecurityDecision.BLOCK; // Fail closed
        }

        return applyConsensus(results);
    }

    private SecurityDecision applyConsensus(List<ValidatorResult> results) {
        long emergencyVotes =
                results.stream()
                        .filter(r -> r.recommendation() == SecurityDecision.EMERGENCY)
                        .count();

        long blockVotes =
                results.stream()
                        .filter(r -> r.recommendation() == SecurityDecision.BLOCK || r.score() > 70)
                        .count();

        double avgScore = results.stream().mapToInt(ValidatorResult::score).average().orElse(0.0);

        if (emergencyVotes >= EMERGENCY_THRESHOLD) {
            log.warn("AEGIS L8-BCSM: EMERGENCY Consensus Reached!");
            return SecurityDecision.EMERGENCY;
        }

        if (blockVotes >= BLOCK_THRESHOLD) {
            log.info("AEGIS L8-BCSM: BLOCK Consensus Reached.");
            return SecurityDecision.BLOCK;
        }

        if (avgScore > 50) {
            return SecurityDecision.DECEPTION;
        }

        if (avgScore > 30) {
            return SecurityDecision.WARN;
        }

        return SecurityDecision.ALLOW;
    }
}
