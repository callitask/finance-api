/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - In-memory representation of a client's request behavior and biometrics across a
 * session.
 *
 * <p>Scope: - Tracks request intervals, anomaly scores, and JA3 fingerprint consistency. - Must
 * never persist PII to the database.
 *
 * <p>Critical Dependencies: - Backend: Utilized heavily by AegisBehavioralEngine (L5-BIE).
 *
 * <p>Security Constraints: - Tenant isolation is enforced natively. Data must not cross-pollinate.
 *
 * <p>Non-Negotiables: - All state modifications must be thread-safe as requests execute
 * concurrently.
 *
 * <p>Change Intent: - Fix-forward remediation: Missing class creation.
 *
 * <p>Future AI Guidance: - Do not remove the AtomicInteger or synchronization blocks. This object
 * is highly concurrent.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial SessionBehaviorProfile
 * implementation. • Resolves behavioral engine compilation gaps. • Phase 3 Remediation Batch.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionBehaviorProfile {

    private final String tenantId;
    private final String sessionIdentifier; // Typically JA3 + IP hash
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final List<Long> requestIntervals = new ArrayList<>();

    private Instant lastRequestTime;
    private double currentRiskScore = 0.0;

    public SessionBehaviorProfile(String tenantId, String sessionIdentifier) {
        this.tenantId = tenantId;
        this.sessionIdentifier = sessionIdentifier;
        this.lastRequestTime = Instant.now();
    }

    public synchronized void recordRequest() {
        requestCount.incrementAndGet();
        Instant now = Instant.now();
        long interval = java.time.Duration.between(lastRequestTime, now).toMillis();

        // Keep only the last 50 intervals to prevent memory leaks
        if (requestIntervals.size() > 50) {
            requestIntervals.remove(0);
        }
        requestIntervals.add(interval);
        this.lastRequestTime = now;
    }

    public synchronized void increaseRiskScore(double penalty) {
        this.currentRiskScore = Math.min(100.0, this.currentRiskScore + penalty);
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSessionIdentifier() {
        return sessionIdentifier;
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public synchronized List<Long> getRequestIntervals() {
        return new ArrayList<>(requestIntervals);
    }

    public synchronized double getCurrentRiskScore() {
        return currentRiskScore;
    }
}
