/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Executes the "Bleed" action for the AEGIS Active Deception Architecture.
 *
 * <p>Scope: - Hijacks the HTTP response stream. - Prevents the server from closing the TCP
 * connection. - Trickles meaningless bytes to keep the attacker's thread hanging.
 *
 * <p>Critical Dependencies: - AegisDeceptionFilter: Triggers this manager. - Virtual Threads (Java
 * 21): Ensures the server's own Tomcat pool is not exhausted by holding connections open.
 *
 * <p>Security Constraints: - Must offload execution to Virtual Threads immediately to prevent DoS
 * against our own system.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 2): • Initial creation
 * utilizing Java 21 Virtual Threads for zero-cost blocking. - EDITED (AEGIS Phase 5 Fix): • Added
 * `holdConnectionInTarpit(HttpServletRequest, HttpServletResponse)` to support L8-BCSM orchestrated
 * tarpit routing. Logs the specific originating IP before ensnaring.
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

  // Utilize Java 21 Virtual Threads to hold thousands of connections open with minimal RAM
  // footprint
  private final ExecutorService tarpitExecutor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Entry point for L8-BCSM consensus decisions. Logs the request context and routes to the tarpit.
   */
  public void holdConnectionInTarpit(HttpServletRequest request, HttpServletResponse response) {
    String ip = request.getHeader("X-Real-IP");
    if (ip == null || ip.isEmpty()) {
      ip = request.getRemoteAddr();
    }
    logger.warn("AEGIS L7-CMCS: L8-BCSM requested tarpit for IP [{}]. Ensnaring connection...", ip);
    ensnare(response);
  }

  public void ensnare(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("text/html");

    // Prevent Cloudflare from closing the connection early
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    response.setHeader("Connection", "keep-alive");

    tarpitExecutor.submit(
        () -> {
          try {
            OutputStream out = response.getOutputStream();
            out.write("\n".getBytes());
            out.flush();

            // Infinite trickle to exhaust attacker resources
            while (!Thread.currentThread().isInterrupted()) {
              Thread.sleep(10000); // 10 second delay
              out.write("0".getBytes()); // Send a single meaningless byte
              out.flush();
            }
          } catch (IOException | InterruptedException e) {
            // Connection finally dropped by attacker or timeout
            logger.debug("AEGIS Tarpit connection terminated by client.");
            Thread.currentThread().interrupt();
          }
        });
  }
}
