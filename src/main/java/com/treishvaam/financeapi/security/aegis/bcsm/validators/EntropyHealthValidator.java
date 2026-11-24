/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 Validator (L0-HEA Node). - Evaluates if the underlying cryptography
 * layer is starving for entropy.
 *
 * <p>Scope: - System-level check, independent of the client request.
 *
 * <p>Security Constraints: - If entropy drops, security operations are unsafe. - Fail-open to
 * `ALLOW` on first boot (no refresh yet — boot refresh may be pending).
 *
 * <p>Change Intent: - Wired real entropy-health via AegisEntropyManager (P3.2, VULN-22b): `0 /
 * ALLOW` when healthy, `50 / WARN` when degraded.
 *
 * <p>Critical Dependencies: - Backend: AegisBcsm, AegisEntropyManager.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of
 * EntropyHealthValidator. - EDITED (Remediation): • Aligned method signature to
 * `evaluate(HttpServletRequest, String)`. • Converted raw strings to `SecurityDecision` enums. •
 * Phase 2 Implementation. - EDITED (Batch 3B - P3.2, VULN-22b): • Injected `AegisEntropyManager`
 * via constructor; return `0 / ALLOW` when `entropyManager.isHealthy()`, `50 / WARN` when degraded.
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import com.treishvaam.financeapi.security.aegis.crypto.AegisEntropyManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class EntropyHealthValidator implements AegisValidator {

    private final AegisEntropyManager entropyManager;

    public EntropyHealthValidator(AegisEntropyManager entropyManager) {
        this.entropyManager = entropyManager;
    }

    @Override
    public ValidatorResult evaluate(HttpServletRequest request, String sessionId) {
        boolean healthy = entropyManager.isHealthy();
        int score = healthy ? 0 : 50;
        SecurityDecision rec = healthy ? SecurityDecision.ALLOW : SecurityDecision.WARN;
        return new ValidatorResult(score, rec, "ENTROPY_NODE");
    }
}
