/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Dead Edge TARPIT route closed (OP-05): GET/POST /api/v1/aegis/tarpit/trap.
 *
 * <p>Scope: - The Cloudflare Worker rewrites poisoned TARPIT traffic into this endpoint. The
 * handler trickle-feeds bytes to exhaust attacker resources on Java 21 Virtual Threads
 * (TarpitManager.holdConnectionInTarpit).
 *
 * <p>Security Constraints: - Route is permitAll() in SecurityConfig but still gated by
 * AegisEdgeValidationFilter (only Worker-rewritten TARPIT traffic enters). - Sets `Cache-Control:
 * no-store, no-cache, must-revalidate` before delegating to TarpitManager.
 *
 * <p>Non-Negotiables: - Never remove the tarpit behavior; the route must stay public.
 *
 * <p>Change Intent: - Close OP-05 (dead TARPIT route) by exposing the existing TarpitManager
 * trickle logic via the internal endpoint.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Batch 3A - P3.3, OP-05): • Created
 * AegisTarpitController exposing TarpitManager.holdConnectionInTarpit on GET/POST
 * /api/v1/aegis/tarpit/trap (why: Worker rewrites poisoned TARPIT traffic there; previously no such
 * route existed — requests died at the Edge with 404s). • Added "/api/v1/aegis/tarpit/trap" to
 * SecurityConfig permitAll() (still gated by AegisEdgeValidationFilter, preserving Zero-Trust
 * TARPIT perimeter). • Behavior preserved: tarpit trickle timing (10s default), status/headers set
 * by TarpitManager.ensnare, Virtual Thread per connection.
 */
package com.treishvaam.financeapi.security.aegis;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Dead Edge TARPIT route closed (OP-05): GET/POST /api/v1/aegis/tarpit/trap. */
@RestController
@RequestMapping("/api/v1/aegis/tarpit")
public class AegisTarpitController {

    private final TarpitManager tarpitManager;

    public AegisTarpitController(TarpitManager tarpitManager) {
        this.tarpitManager = tarpitManager;
    }

    @RequestMapping(
            value = "/trap",
            method = {RequestMethod.GET, RequestMethod.POST})
    public void trap(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        tarpitManager.holdConnectionInTarpit(request, response);
    }
}
