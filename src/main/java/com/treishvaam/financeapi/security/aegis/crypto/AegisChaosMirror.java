/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Counter-Attack System (L7-CMCS) orchestration module. Coordinates connection
 * manipulation (Tarpits) and CPU resource exhaustion (Proof-of-Work puzzles) against hostile
 * clients.
 *
 * <p>Scope: - Executes defensive countermeasures securely over Java 21 Virtual Threads. - Does NOT
 * handle standard requests, only executes when Byzantine Consensus returns a TARPIT or CHALLENGE
 * decision.
 *
 * <p>Critical Dependencies: - Backend: Relies on `TarpitManager` for 1-byte trickles, and
 * `PowChallengeIssuer` for CPU burnout hashes.
 *
 * <p>Security Constraints: - Must not block standard Tomcat worker threads. All heavy I/O
 * operations must be offloaded to Virtual Threads.
 *
 * <p>Non-Negotiables: - Must strictly operate passively. Counter-attacks must be legal (resource
 * exhaustion of attacker connection only).
 *
 * <p>Change Intent: - Phase 4: Implemented the central Chaos Mirror module to integrate isolated
 * defensive mechanics into the SecurityFilterChain.
 *
 * <p>Future AI Guidance: - If new counter-attack strategies (e.g. Recursive JSON loops) are added,
 * wire them through this class.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Implemented `AegisChaosMirror`
 * orchestrator. • Why it was added: Provides a single interface for `AegisMainFilter` to trigger
 * dynamic counter-attacks without leaking logic. • Date: 2026-05-22
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis.crypto;

import com.treishvaam.financeapi.security.aegis.TarpitManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AegisChaosMirror {

    private final TarpitManager tarpitManager;
    private final PowChallengeIssuer powChallengeIssuer;

    /** Tarpits the connection by responding very slowly. */
    public void activateTarpit(
            HttpServletRequest request, HttpServletResponse response, int delayMs) {
        log.warn(
                "AEGIS L7-CMCS: Activating TARPIT for {} delay {}ms",
                request.getRemoteAddr(),
                delayMs);
        try {
            tarpitManager.tarpitConnection(request, response, delayMs);
        } catch (Exception e) {
            log.error("Failed to tarpit connection", e);
        }
    }

    /** Issues a CPU-exhausting Proof-of-Work challenge to the attacker. */
    public void issuePoWChallenge(HttpServletRequest request, HttpServletResponse response) {
        log.warn("AEGIS L7-CMCS: Issuing PoW Hash Burn to {}", request.getRemoteAddr());
        try {
            powChallengeIssuer.issueChallenge(request, response);
        } catch (IOException e) {
            log.error("Failed to issue PoW challenge", e);
        }
    }
}
