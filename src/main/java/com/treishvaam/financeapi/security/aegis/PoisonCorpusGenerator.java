/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implements AEGIS Layer 4 (ADA) Payload Generation. - Generates highly plausible but
 * entirely fake responses (e.g., .env files, WP-Admin pages, JWTs).
 *
 * <p>Scope: - Feeds the AegisDeceptionEngine with poisoned corpus data. - Must never return real
 * system states.
 *
 * <p>Critical Dependencies: - Backend: CanaryTokenService for embedding tracking hashes.
 *
 * <p>Security Constraints: - Must not contain any actual secrets.
 *
 * <p>Change Intent: - Executing AEGIS Orchestrator Phase 3.2.
 *
 * <p>Future AI Guidance: - Add more poisoned payloads (e.g., AWS config, SSH keys) as attacker
 * patterns evolve.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation for Phase 3.2 ADA
 * Orchestration. • Implemented fake .env, JWT, and admin HTML generation.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import org.springframework.stereotype.Service;

@Service
public class PoisonCorpusGenerator {

    public String generateFakeEnv(String canaryToken) {
        return "DB_HOST=127.0.0.1\n"
                + "DB_USER=root\n"
                + "DB_PASS=P0wnd_"
                + canaryToken
                + "\n"
                + "AWS_ACCESS_KEY_ID="
                + canaryToken
                + "\n"
                + "AWS_SECRET_ACCESS_KEY=aegis_fake_secret_v1_L4_ADA\n"
                + "JWT_SECRET=super_secret_dev_key_do_not_commit\n"
                + "STRIPE_API_KEY=sk_live_"
                + canaryToken.toLowerCase()
                + "\n";
    }

    public String generateFakeJwt(String canaryToken) {
        return "{"
                + "\"status\":\"success\","
                + "\"config_version\":\"1.0.4\","
                + "\"debug_token\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJuYW1lIjoiZGV2X2FkbWluIiwiZXhwIjoxNzk5OTk5OTk5fQ."
                + canaryToken
                + "\""
                + "}";
    }

    public String generateFakeAdminHtml(String canaryToken) {
        return "<!DOCTYPE html>\n<html><head><title>Admin Login Portal</title></head>\n<body>\n"
                + "<h1>Treishvaam Group - Secure Admin Portal</h1>\n"
                + "\n"
                + "<form method='POST' action='/wp-login.php'>\n"
                + "  <label>Username:</label> <input type='text' name='user'/>\n"
                + "  <label>Password:</label> <input type='password' name='pass'/>\n"
                + "  <button type='submit'>Login</button>\n"
                + "</form>\n</body>\n</html>";
    }
}
