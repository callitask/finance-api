package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Application-layer SQL injection protection and request sanitization backed by
 * Redis.
 *
 * <p>Scope: - Validates search queries and user inputs against known SQLi patterns.
 *
 * <p>Critical Dependencies: - Backend: RedisTemplate for distributed blocking state.
 *
 * <p>Security Constraints: - Block suspicious IPs temporarily to prevent automated probing and DB
 * load.
 *
 * <p>Non-Negotiables: - Must fail fast and not block legitimate queries unnecessarily.
 *
 * <p>Change Intent: - Implement SEC-10: Application-layer SQLi protection at the filter layer.
 *
 * <p>Future AI Guidance: - Expand regex patterns cautiously to avoid false positives with valid
 * user input.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Phase 4 - SEC-10 Fix): • Created
 * QuerySanitizationService. • Why it was added: Provides a Redis-backed query validation layer to
 * prevent SQL injection from reaching the database. • Date: Phase 4 Implementation.
 *
 * <p>- EDITED (Phase 5 - WAF False Positive Fix): • Refined SQLi regex pattern to target chained
 * execution syntax (;\\s*(drop|exec...)) rather than blocking lone semicolons. • Why: Resolves a
 * 400 Bad Request false positive caused by Tiptap injecting inline CSS (style="width: 50%; float:
 * left;") during video uploads, without degrading Zero-Trust SQLi protection.
 */
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QuerySanitizationService {

    private static final Logger logger = LoggerFactory.getLogger(QuerySanitizationService.class);

    @Autowired private RedisTemplate<String, String> redisTemplate;

    // Pre-compiled patterns for known SQLi signatures
    private static final List<Pattern> SQL_INJECTION_PATTERNS =
            List.of(
                    Pattern.compile(
                            "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|xp_|sp_)",
                            Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "(--|;\\s*(?i)(drop|alter|create|truncate|delete|insert|update|exec|declare|xp_)|/\\*|\\*/|xp_|WAITFOR|BENCHMARK|SLEEP)",
                            Pattern.CASE_INSENSITIVE),
                    Pattern.compile("('|(\\')|(\\\\')|(%27)|(%2527))", Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "(\\bOR\\b|\\bAND\\b)\\s+([\\w\\s]+=\\s*[\\w\\s]+|'[^']*'='[^']*')",
                            Pattern.CASE_INSENSITIVE));

    // Block list with TTL: if an IP sends SQLi, block for 1 hour
    public boolean isSuspicious(String input, String clientIp) {
        if (input == null) return false;

        String cacheKey = "sqli:blocked:" + clientIp;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            return true; // Already flagged and blocked
        }

        for (Pattern p : SQL_INJECTION_PATTERNS) {
            if (p.matcher(input).find()) {
                // Cache the offending IP for 1 hour
                redisTemplate.opsForValue().set(cacheKey, "1", Duration.ofHours(1));
                logger.warn(
                        "SECURITY: Potential SQL injection from IP: {} input: {}",
                        clientIp,
                        input.substring(0, Math.min(100, input.length())));
                return true;
            }
        }
        return false;
    }
}
