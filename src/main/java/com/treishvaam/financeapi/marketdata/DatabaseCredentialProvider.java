/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Securely provides database credentials for Python script execution.
 *
 * <p>Scope: - Used exclusively by MarketDataService to avoid exposing DB credentials in plain
 * String fields.
 *
 * <p>Security Constraints: - On-demand reads from Spring {@code Environment}; no mutable credential
 * fields (previously zeroed by destructive {@code clearSensitiveData()} after the first STARTUP
 * Python run — root cause of VULN-20).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created DatabaseCredentialProvider to
 * encapsulate DB credentials (SEC-05). • Why: Prevents DB credentials from being stored as plain
 * String fields in the long-lived MarketDataService bean, mitigating memory exposure risks.
 *
 * <p>- EDITED (Batch 2B - P2.7, VULN-20): • Removed mutable singleton {@code char[]
 * dbUrl/dbUsername/ dbPassword} fields and destructive {@code clearSensitiveData()} zeroing. •
 * Injected Spring {@code Environment} via constructor; {@code getUrl()/getUsername()/getPassword()}
 * now return {@code environment.getProperty("spring.datasource.*", "")} on demand. • {@code
 * clearSensitiveData()} retained as a documented no-op so existing callers compile unchanged.
 */
package com.treishvaam.financeapi.marketdata;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCredentialProvider {

    private final Environment environment;

    public DatabaseCredentialProvider(Environment environment) {
        this.environment = environment;
    }

    public String getUrl() {
        return environment.getProperty("spring.datasource.url", "");
    }

    public String getUsername() {
        return environment.getProperty("spring.datasource.username", "");
    }

    public String getPassword() {
        return environment.getProperty("spring.datasource.password", "");
    }

    /**
     * No-op retained for source compatibility (VULN-20). Previously zeroed the credential {@code
     * char[]} fields permanently after the first STARTUP run; credentials are now resolved on
     * demand from the Environment, so there is nothing to clear.
     */
    public void clearSensitiveData() {
        // Deliberate no-op (VULN-20).
    }
}
