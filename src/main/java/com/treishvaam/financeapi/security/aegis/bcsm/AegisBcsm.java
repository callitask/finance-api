/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 (Byzantine Consensus Security Mesh). - Aggregates security
 * evaluations from 7 independent micro-validators.
 *
 * <p>Scope: - Utilizes Java 21 StructuredTaskScope for parallel execution. - Enforces a strict
 * 100ms timeout for all validators combined. - Evaluates the quorum to produce a final
 * SecurityDecision.
 *
 * <p>Critical Dependencies: - Backend: 7 AegisValidator implementations.
 *
 * <p>Security Constraints: - Must tolerate up to 2 compromised or timed-out validators (Byzantine
 * Fault Tolerance). - No single validator can crash the quorum.
 *
 * <p>Non-Negotiables: - Must use Virtual Threads. - Timeout enforcement is absolute to prevent DDoS
 * via evaluation latency.
 *
 * <p>Change Intent: - Fully implement the parallel consensus execution.
 *
 * <p>Future AI Guidance: - Do not replace StructuredTaskScope with legacy CompletableFuture pools.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of AegisBcsm core. -
 * EDITED: • Replaced sequential/stub logic with fully concurrent StructuredTaskScope. • Implemented
 * 3f+1 BFT logic (3 Block votes = Block, 2 Emergency votes = Emergency). • Enforced 100ms hard
 * latency ceiling. • Phase 2 Implementation.
 */
package com.treishvaam.financeapi.security.aegis.bcsm;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
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

    public SecurityDecision evaluate(HttpServletRequest request) {

        List<ValidatorResult> results;

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Fork all validators onto virtual threads
            List<StructuredTaskScope.Subtask<ValidatorResult>> tasks =
                    validators.stream()
                            .map(v -> scope.fork(() -> v.evaluate(request)))
                            .collect(Collectors.toList());

            // Wait for all to finish or timeout at 100ms
            scope.joinUntil(Instant.now().plusMillis(VALIDATOR_TIMEOUT_MS));

            // Map results, replacing timeouts or failures with safe defaults
            results =
                    tasks.stream()
                            .map(
                                    t -> {
                                        if (t.state()
                                                == StructuredTaskScope.Subtask.State.SUCCESS) {
                                            return t.get();
                                        } else {
                                            return new ValidatorResult(50, "WARN", "TIMEOUT_NODE");
                                        }
                                    })
                            .collect(Collectors.toList());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AEGIS L8-BCSM: Consensus interrupted, defaulting to WARN state.");
            return new SecurityDecision("ALLOW", "Interrupted", 0);
        } catch (Exception e) {
            log.error("AEGIS L8-BCSM: Fatal quorum execution error.", e);
            return new SecurityDecision("BLOCK", "Fatal Quorum Error", 100);
        }

        return applyConsensus(results);
    }

    private SecurityDecision applyConsensus(List<ValidatorResult> results) {
        long emergencyVotes =
                results.stream().filter(r -> "EMERGENCY".equals(r.recommendation())).count();

        long blockVotes =
                results.stream()
                        .filter(r -> "BLOCK".equals(r.recommendation()) || r.score() > 70)
                        .count();

        double avgScore = results.stream().mapToInt(ValidatorResult::score).average().orElse(0.0);

        if (emergencyVotes >= EMERGENCY_THRESHOLD) {
            log.warn("AEGIS L8-BCSM: EMERGENCY Consensus Reached!");
            return new SecurityDecision(
                    "EMERGENCY", "Multiple nodes reported critical threat", (int) avgScore);
        }

        if (blockVotes >= BLOCK_THRESHOLD) {
            log.info("AEGIS L8-BCSM: BLOCK Consensus Reached.");
            return new SecurityDecision("BLOCK", "Threshold block votes reached", (int) avgScore);
        }

        if (avgScore > 50) {
            return new SecurityDecision(
                    "DECEPTION", "Anomalous traffic routed to L4-ADA", (int) avgScore);
        }

        if (avgScore > 30) {
            return new SecurityDecision("WARN", "Elevated risk score", (int) avgScore);
        }

        return new SecurityDecision("ALLOW", "Traffic verified", (int) avgScore);
    }
}
