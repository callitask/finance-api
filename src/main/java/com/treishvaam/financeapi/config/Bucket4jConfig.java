/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Configuration for Redis-backed distributed rate limiting via Bucket4j.
 *
 * <p>Scope: - Provides the ProxyManager bean to the RateLimitingFilter.
 *
 * <p>Critical Dependencies: - Redis connection factory.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created Bucket4jConfig to expose a
 * ProxyManager<String> backed by Lettuce/Redis. • Why: Required for migrating RateLimitingFilter to
 * a distributed cache to prevent bypasses via IP rotation across instances (SEC-02).
 */
package com.treishvaam.financeapi.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jConfig {

  @Bean
  public ProxyManager<String> proxyManager(RedisClient redisClient) {
    return LettuceBasedProxyManager.builderFor(redisClient)
        .withExpirationStrategy(
            io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
        .build();
  }
}
