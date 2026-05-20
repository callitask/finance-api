/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Configuration for Redis-backed distributed rate limiting via Bucket4j.
 *
 * <p>Scope: - Provides the ProxyManager bean to the RateLimitingFilter.
 *
 * <p>Critical Dependencies: - Redis connection factory.
 *
 * <p>Security Constraints: - Must never manually construct RedisClient using @Value properties. -
 * Must exclusively rely on Spring's RedisConnectionFactory to inherit centralized security, RESP3
 * negotiation, and SSL settings.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created Bucket4jConfig to expose a
 * ProxyManager<byte[]> backed by Lettuce/Redis. • Why: Required for migrating RateLimitingFilter to
 * a distributed cache to prevent bypasses via IP rotation across instances (SEC-02).
 *
 * <p>- EDITED (Hotfix): • Changed return type of proxyManager from ProxyManager<String> to
 * ProxyManager<byte[]> to match Bucket4j 8.x Lettuce native key requirement. • Explicitly declared
 * a `RedisClient` bean to parse `spring.data.redis` properties, avoiding missing bean crashes on
 * startup. • Why: Addressed Maven compilation failure `incompatible types: ProxyManager<byte[]>
 * cannot be converted to ProxyManager<String>`.
 *
 * <p>- EDITED (Enterprise Observability & Stability): • Removed the custom `RedisClient` bean and
 * manual `@Value` property injections. • Injected Spring's `RedisConnectionFactory` and extracted
 * the native Lettuce client via casting. • Why: The manual client bypassed Spring Boot 3.4.x
 * authentication protocols, resulting in a `NOAUTH HELLO` connection crash and a fatal Tomcat
 * memory leak on the Netty `lettuce-eventExecutorLoop` threads. Aligning with Spring's connection
 * factory guarantees graceful shutdown and authenticated handshakes.
 */
package com.treishvaam.financeapi.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class Bucket4jConfig {

  @Bean
  public ProxyManager<byte[]> proxyManager(RedisConnectionFactory redisConnectionFactory) {

    // Extract the native, fully-authenticated Lettuce client managed by Spring Boot
    LettuceConnectionFactory lettuceConnectionFactory =
        (LettuceConnectionFactory) redisConnectionFactory;
    RedisClient redisClient = (RedisClient) lettuceConnectionFactory.getNativeClient();

    return LettuceBasedProxyManager.builderFor(redisClient)
        .withExpirationStrategy(
            io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
        .build();
  }
}
