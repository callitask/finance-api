/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 8 Byzantine Consensus Security Mesh (L8-BCSM). - Ensures no single point of
 * failure in security decision-making.
 *
 * <p>Scope: - Executes injected AegisValidators in parallel using Virtual Threads. - Applies quorum
 * logic to determine final request fate.
 *
 * <p>Critical Dependencies: - Requires Java 21 Virtual Threads for 0ms Tomcat thread blocking.
 *
 * <p>Security Constraints: - If 2+ validators vote EMERGENCY, the whole system must shift (MTD
 * trigger).
 *
 * <p>Change Intent: - Centralize security decisions away from single-filter failure points.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Parallel execution logic via
 * CompletableFuture. • Byzantine voting rules.
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
    private static final int BLOCK_THRESHOLD = 2; // 2+ blocks = reject
    private static final int EMERGENCY_THRESHOLD = 2; // 2+ emergency = MTD shift
    private static final long VALIDATOR_TIMEOUT_MS = 100;

    private final List<AegisValidator> validators;
    private final ExecutorService virtualExecutor;

    public AegisBcsm(List<AegisValidator> validators) {
        this.validators = validators;
        // Use Java 21 Virtual Threads for non-blocking parallel validator execution
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SecurityDecision evaluate(HttpServletRequest request, String sessionId) {
        if (validators == null || validators.isEmpty()) {
            return SecurityDecision.ALLOW; // Fail open if no validators registered yet
        }

        List<CompletableFuture<ValidatorResult>> futures =
                validators.stream()
                        .map(
                                v ->
                                        CompletableFuture.supplyAsync(
                                                        () -> v.evaluate(request, sessionId),
                                                        virtualExecutor)
                                                .orTimeout(
                                                        VALIDATOR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                                                .exceptionally(ex -> ValidatorResult.timeout()))
                        .collect(Collectors.toList());

        List<ValidatorResult> results =
                futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

        return applyConsensus(results);
    }

    private SecurityDecision applyConsensus(List<ValidatorResult> results) {
        long emergencyVotes =
                results.stream()
                        .filter(r -> r.recommendation() == SecurityDecision.EMERGENCY)
                        .count();
        long blockVotes =
                results.stream()
                        .filter(r -> r.recommendation() == SecurityDecision.BLOCK || r.score() > 80)
                        .count();
        long deceptionVotes =
                results.stream()
                        .filter(r -> r.recommendation() == SecurityDecision.DECEPTION)
                        .count();

        double avgScore = results.stream().mapToInt(ValidatorResult::score).average().orElse(0);

        if (emergencyVotes >= EMERGENCY_THRESHOLD) {
            log.warn("AEGIS L8-BCSM: CONSENSUS REACHED -> EMERGENCY. Triggering MTD Shift.");
            return SecurityDecision.EMERGENCY;
        }
        if (blockVotes >= BLOCK_THRESHOLD) {
            log.warn("AEGIS L8-BCSM: CONSENSUS REACHED -> BLOCK.");
            return SecurityDecision.BLOCK;
        }
        if (deceptionVotes >= 1 || avgScore > 60) {
            log.info("AEGIS L8-BCSM: CONSENSUS REACHED -> DECEPTION. Routing to L4-ADA.");
            return SecurityDecision.DECEPTION;
        }
        if (avgScore > 30) {
            return SecurityDecision.WARN;
        }

        return SecurityDecision.ALLOW;
    }
}
