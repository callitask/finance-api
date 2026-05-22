/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Executes the "Bleed" action for the AEGIS Active Deception Architecture.
 *
 * Scope:
 * - Hijacks the HTTP response stream.
 * - Prevents the server from closing the TCP connection.
 * - Trickles meaningless bytes to keep the attacker's thread hanging.
 *
 * Critical Dependencies:
 * - AegisDeceptionFilter: Triggers this manager.
 * - AegisChaosMirror: Triggers dynamic delay tarpits based on behavior scores.
 * - Virtual Threads (Java 21): Ensures the server's own Tomcat pool is not exhausted by holding connections open.
 *
 * Security Constraints:
 * - Must offload execution to Virtual Threads immediately to prevent DoS against our own system.
 *
 * Non-Negotiables:
 * - Thread.sleep must run inside the virtual thread executor, never on the Tomcat thread.
 *
 * Change Intent:
 * - Fixed `AegisChaosMirror` dependency by adding dynamic delay support via `tarpitConnection`.
 *
 * Future AI Guidance:
 * - If modifying sleep timers, ensure they remain mathematically proportional to the attacker's risk score.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED (AEGIS Phase 2):
 * • Initial creation utilizing Java 21 Virtual Threads for zero-cost blocking.
 * * - EDITED (AEGIS Phase 5 Fix):
 * • Added `holdConnectionInTarpit(HttpServletRequest, HttpServletResponse)` to support L8-BCSM orchestrated tarpit routing. Logs the specific originating IP before ensnaring.
 * * - EDITED:
 * • Added `tarpitConnection(request, response, delayMs)` and parameterized `ensnare` to support dynamic delays.
 * • Why: `AegisChaosMirror` requires dynamic trickling speeds based on behavioral scoring. Fixing compilation error without rolling back Phase 4 progress.
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated, rewritten, or regenerated.
 * Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TarpitManager {

    private static final Logger logger = LoggerFactory.getLogger(TarpitManager.class);

    // Utilize Java 21 Virtual Threads to hold thousands of connections open with minimal RAM footprint
    private final ExecutorService tarpitExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Entry point for L8-BCSM consensus decisions. Logs the request context and routes to the
     * tarpit with a default 10,000ms delay.
     */
    public void holdConnectionInTarpit(HttpServletRequest request, HttpServletResponse response) {
        String ip = extractIp(request);
        logger.warn("AEGIS L7-CMCS: L8-BCSM requested tarpit for IP [{}]. Ensnaring connection with default delay...", ip);
        ensnare(response, 10000);
    }

    /**
     * Dynamic tarpit entry point used by AegisChaosMirror.
     * Allows variable delay based on the severity of the infraction.
     */
    public void tarpitConnection(HttpServletRequest request, HttpServletResponse response, int delayMs) {
        String ip = extractIp(request);
        logger.warn("AEGIS L7-CMCS: Dynamic Tarpit requested for IP [{}]. Ensnaring with {}ms trickle delay...", ip, delayMs);
        ensnare(response, delayMs);
    }

    private String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * Core ensnare logic. Overloaded for backward compatibility with older implementations.
     */
    public void ensnare(HttpServletResponse response) {
        ensnare(response, 10000);
    }

    /**
     * Parameterized ensnare logic supporting dynamically scaling trickles.
     */
    public void ensnare(HttpServletResponse response, int delayMs) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html");

        // Prevent Cloudflare from closing the connection early
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Connection", "keep-alive");

        tarpitExecutor.submit(() -> {
            try {
                OutputStream out = response.getOutputStream();
                out.write("\n".getBytes());
                out.flush();

                // Infinite trickle to exhaust attacker resources
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(delayMs); // Dynamic delay based on threat score
                    out.write("0".getBytes()); // Send a single meaningless byte
                    out.flush();
                }
            } catch (IOException | InterruptedException e) {
                // Connection finally dropped by attacker or timeout
                logger.debug("AEGIS Tarpit connection terminated by client or interrupted.");
                Thread.currentThread().interrupt();
            }
        });
    }
}