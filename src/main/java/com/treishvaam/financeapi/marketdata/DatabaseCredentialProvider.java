/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Securely provides database credentials for Python script execution.
 *
 * <p>Scope: - Used exclusively by MarketDataService to avoid exposing DB credentials in plain
 * String fields.
 *
 * <p>Security Constraints: - Must use char[] instead of String to prevent persistence in heap
 * memory dumps.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created DatabaseCredentialProvider to
 * encapsulate DB credentials (SEC-05). • Why: Prevents DB credentials from being stored as plain
 * String fields in the long-lived MarketDataService bean, mitigating memory exposure risks.
 */
package com.treishvaam.financeapi.marketdata;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCredentialProvider {
  @Value("${spring.datasource.url}")
  private char[] dbUrl;

  @Value("${spring.datasource.username}")
  private char[] dbUsername;

  @Value("${spring.datasource.password}")
  private char[] dbPassword;

  public String getUrl() {
    return new String(dbUrl);
  }

  public String getUsername() {
    return new String(dbUsername);
  }

  public String getPassword() {
    return new String(dbPassword);
  }

  public void clearSensitiveData() {
    if (dbUrl != null) Arrays.fill(dbUrl, '\0');
    if (dbUsername != null) Arrays.fill(dbUsername, '\0');
    if (dbPassword != null) Arrays.fill(dbPassword, '\0');
  }
}
