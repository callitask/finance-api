/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - L8-BCSM Security Decision Enum.
 *
 * <p>Scope: - Defines the strict action states the Byzantine Consensus Mesh can enforce.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation for Phase 4/5
 * integration.
 */
package com.treishvaam.financeapi.security.aegis.bcsm;

public enum SecurityDecision {
  ALLOW,
  WARN,
  BLOCK,
  DECEPTION,
  TARPIT,
  EMERGENCY
}
