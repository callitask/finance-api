/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 6 Moving Target Defense (L6-MTD) Controller. - Orchestrates the continuous
 * shifting of infrastructure parameters.
 *
 * <p>Scope: - Triggers daily Manifest Rotations. - Triggers PQC Keypair rotations weekly. - Exposes
 * an Emergency Rotation API for the RabbitMQ event bus.
 *
 * <p>Critical Dependencies: - AegisTemporalPathManager - AegisPqcKeyStore
 *
 * <p>Security Constraints: - The emergency trigger must be heavily authenticated (internal loopback
 * only).
 *
 * <p>Non-Negotiables: - Rotations must never drop active database connections; they apply to new
 * traffic.
 *
 * <p>Change Intent: - Establish the automated mutation cadence for the AEGIS environment.
 *
 * <p>Future AI Guidance: - Integrate with Cloudflare API to push the manifest to Workers KV
 * automatically during the `rotateDaily()` cron cycle.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Cron scheduling for daily path rotation.
 * • Cron scheduling for weekly PQC key rotation. • Phase 1/2 Orchestration Batch.
 */
package com.treishvaam.financeapi.security.aegis.mtd;

import com.treishvaam.financeapi.security.aegis.crypto.AegisPqcKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AegisMtdController {

  private static final Logger log = LoggerFactory.getLogger(AegisMtdController.class);

  private final AegisTemporalPathManager temporalPathManager;
  private final AegisPqcKeyStore pqcKeyStore;

  public AegisMtdController(
      AegisTemporalPathManager temporalPathManager, AegisPqcKeyStore pqcKeyStore) {
    this.temporalPathManager = temporalPathManager;
    this.pqcKeyStore = pqcKeyStore;
  }

  // Runs every 24 hours at 03:00 AM server time
  @Scheduled(cron = "0 0 3 * * *")
  public void rotateDaily() {
    log.info("AEGIS L6-MTD: Executing Daily moving-target rotation sequence...");
    temporalPathManager.rotateManifest();
    // TODO: Push Manifest to Cloudflare Workers KV
  }

  // Runs every 7 days at 04:00 AM server time
  @Scheduled(cron = "0 0 4 * * SUN")
  public void rotateWeekly() {
    log.info("AEGIS L6-MTD: Executing Weekly moving-target rotation sequence...");
    pqcKeyStore.generateNewKeypair();
    // Force a path rotation immediately after key rotation to re-sign the manifest
    temporalPathManager.rotateManifest();
  }

  // Called dynamically by AegisBcsm or RabbitMQ when under severe attack
  public void triggerEmergencyRotation(String triggerSource) {
    log.warn(
        "AEGIS L6-MTD: EMERGENCY ROTATION TRIGGERED by [{}]! Shifting infrastructure targets...",
        triggerSource);
    pqcKeyStore.generateNewKeypair();
    temporalPathManager.rotateManifest();
    log.info("AEGIS L6-MTD: Emergency target shift complete.");
  }
}
