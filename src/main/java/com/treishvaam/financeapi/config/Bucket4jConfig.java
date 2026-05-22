/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Configuration for Redis-backed distributed rate limiting via Bucket4j.
 *
 * <p>Scope: - Provides the ProxyManager bean to the RateLimitingFilter.
 *
 * <p>Critical Dependencies: - Redis connection factory.
 *
 * <p>Security Constraints: - Must never manually hardcode Redis credentials. - Must securely inject
 * Spring's RedisProperties to ensure environment parity with Spring Data.
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
 * memory leak on the Netty `lettuce-eventExecutorLoop` threads.
 *
 * <p>- EDITED (Startup Hang & Netty Deadlock Fix): • Re-introduced a dedicated, isolated
 * `RedisClient` bean, but dynamically injected Spring's `RedisProperties` instead of `@Value`. •
 * Why: Reusing Spring's shared `LettuceConnectionFactory` for Bucket4j's eager proxy generation
 * caused a silent thread deadlock on the Netty event loop, freezing application startup for >6
 * minutes. This hybrid approach provides credential safety while physically separating Bucket4j's
 * connection lifecycle from Spring Data.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient(RedisProperties properties) {
        RedisURI.Builder uriBuilder =
                RedisURI.builder().withHost(properties.getHost()).withPort(properties.getPort());

        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            uriBuilder.withPassword(properties.getPassword().toCharArray());
        }

        return RedisClient.create(uriBuilder.build());
    }

    @Bean
    public ProxyManager<byte[]> proxyManager(RedisClient bucket4jRedisClient) {
        return LettuceBasedProxyManager.builderFor(bucket4jRedisClient)
                .withExpirationStrategy(
                        io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }
}
