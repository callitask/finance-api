/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Configuration for Redis-backed distributed rate limiting via Bucket4j.
 *
 * <p>Scope: - Provides the ProxyManager bean to the RateLimitingFilter.
 *
 * <p>Critical Dependencies: - Redis connection factory.
 *
 * <p>Security Constraints: - Isolated Connection: Bucket4j MUST maintain its own RedisClient to
 * prevent Netty event-loop deadlocks with Spring Data Redis during application startup. - RESP3
 * Protocol: Must explicitly authenticate with the 'default' username to satisfy Redis 7 ACLs.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created Bucket4jConfig to expose a
 * ProxyManager<byte[]> backed by Lettuce/Redis.
 *
 * <p>- EDITED (Hotfix): • Changed return type of proxyManager from ProxyManager<String> to
 * ProxyManager<byte[]>.
 *
 * <p>- EDITED (Enterprise Observability & Stability): • Removed manual `RedisClient` and injected
 * Spring's `RedisConnectionFactory`. • Why: Attempted to fix `NOAUTH HELLO`.
 *
 * <p>- EDITED (Deadlock & RESP3 Fix): • Reverted to standalone `RedisClient` bean but explicitly
 * added `.withAuthentication("default", password)` for Redis 7 RESP3 compatibility. • Why: Stealing
 * Spring's native client via `getNativeClient()` caused a fatal Netty deadlock, blocking the Spring
 * Boot ApplicationReadyEvent and causing the container to hang indefinitely (unhealthy). The
 * `withAuthentication` resolves the original `NOAUTH HELLO` error.
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
      // Enterprise Fix: Redis 6/7 RESP3 protocol requires explicit ACL username for the AUTH
      // command.
      uriBuilder.withAuthentication("default", redisPassword.toCharArray());
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
