/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Mathematical utility to calculate the Shannon Entropy of strings (headers,
 * payloads, JA3).
 *
 * <p>Scope: - Strictly bounded to pure functional mathematical calculation.
 *
 * <p>Critical Dependencies: - Backend: AegisBehavioralEngine depends on this to detect DGA (Domain
 * Generation Algorithm) or randomized bot payloads.
 *
 * <p>Security Constraints: - Must operate quickly without complex object instantiation to prevent
 * CPU exhaustion.
 *
 * <p>Non-Negotiables: - Pure function. No external dependencies.
 *
 * <p>Change Intent: - Fix-forward remediation: Math utility required for L5-BIE scoring.
 *
 * <p>Future AI Guidance: - Do not attempt to add external math libraries; native Java 8+ streams
 * are sufficient.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Native Shannon Entropy calculation
 * algorithm. • Phase 3 Remediation Batch.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ShannonEntropyCalculator {

    /**
     * Calculates the Shannon Entropy (H) of a given string. High entropy indicates high randomness
     * (potential bot/DGA traffic).
     *
     * @param input the string to analyze
     * @return the entropy value in bits
     */
    public static double calculate(String input) {
        if (input == null || input.isEmpty()) {
            return 0.0;
        }

        Map<Character, Long> charCounts =
                input.chars()
                        .mapToObj(c -> (char) c)
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        int length = input.length();
        double entropy = 0.0;

        for (Long count : charCounts.values()) {
            double probability = (double) count / length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }

        return entropy;
    }
}
