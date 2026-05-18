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
 * a distributed cache to prevent bypasses via IP rotation across instances (SEC-02). * - EDITED
 * (Hotfix): • Changed return type of proxyManager from ProxyManager<String> to ProxyManager<byte[]>
 * to match Bucket4j 8.x Lettuce native key requirement. • Explicitly declared a `RedisClient` bean
 * to parse `spring.data.redis` properties, avoiding missing bean crashes on startup. • Why:
 * Addressed Maven compilation failure `incompatible types: ProxyManager<byte[]> cannot be converted
 * to ProxyManager<String>`.
 */
package com.treishvaam.financeapi.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jConfig {

  @Value("${spring.data.redis.host:localhost}")
  private String redisHost;

  @Value("${spring.data.redis.port:6379}")
  private int redisPort;

  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Bean
  public RedisClient redisClient() {
    RedisURI.Builder uriBuilder = RedisURI.builder().withHost(redisHost).withPort(redisPort);

    if (redisPassword != null && !redisPassword.isEmpty()) {
      uriBuilder.withPassword(redisPassword.toCharArray());
    }

    return RedisClient.create(uriBuilder.build());
  }

  @Bean
  public ProxyManager<byte[]> proxyManager(RedisClient redisClient) {
    return LettuceBasedProxyManager.builderFor(redisClient)
        .withExpirationStrategy(
            io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
        .build();
  }
}
