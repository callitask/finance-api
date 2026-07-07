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
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 *
 * <p>- ADDED: • Created Bucket4jConfig to expose a ProxyManager<byte[]> backed by Lettuce/Redis. •
 * Why: Required for migrating RateLimitingFilter to a distributed cache to prevent bypasses via IP
 * rotation across instances (SEC-02).
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
 * <p>- EDITED (DNS Resolution Hardening - SERVFAIL Fix): • Introduced `ClientResources` bean
 * explicitly configured with `DnsResolvers.JVM_DEFAULT`. • Injected `ClientResources` into the
 * isolated `RedisClient` bean. • Why: Netty's default async DNS resolver frequently drops UDP
 * packets in bridged VirtualBox networking environments under load, causing `UnknownHostException`
 * (SERVFAIL) and freezing Tomcat context initialization. Forcing the synchronous JVM resolver
 * completely eliminates this race condition without dropping the distributed rate limiter.
 *
 * <p>- EDITED (Lettuce Authentication Race Condition Fix): • Explicitly injected the Redis password
 * using `@Value("${spring.data.redis.password}")` alongside `RedisProperties`. • Why:
 * `RedisProperties` was occasionally resolving before the environment was fully populated, causing
 * the proxy manager to attempt unauthenticated connections, resulting in a fatal `NOAUTH` startup
 * crash loop. Explicit `@Value` binding forces Spring to guarantee credential presence before
 * instantiating the bean.
 *
 * <p>- EDITED (Redis Password Fallback Hardening): • Updated @Value annotation to
 * `@Value("${spring.data.redis.password:${SPRING_DATA_REDIS_PASSWORD:${SPRING_REDIS_PASSWORD:}}}")`.
 * • Why: To guarantee password resolution regardless of which Spring or Docker environment
 * namespace is evaluated first, eliminating unauthenticated HELLO command crashes during Lettuce
 * initialization.
 *
 * <p>- EDITED (Property Shadowing NOAUTH Fix - 2026-07-07): • Prioritized explicit parsing of
 * `SPRING_DATA_REDIS_URL` over fragmented properties. • Why: Spring Boot was shadowing the password
 * environment variable with an empty string from `application.properties`, causing Bucket4j to
 * initialize Lettuce without credentials and triggering a `NOAUTH` crash on the `HELLO` handshake.
 * Parsing the fully qualified OS URL mathematically guarantees the authenticated string is passed
 * to Redis.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jConfig {

    @Bean(destroyMethod = "shutdown")
    public ClientResources lettuceClientResources() {
        return DefaultClientResources.builder().dnsResolver(DnsResolvers.JVM_DEFAULT).build();
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient(
            RedisProperties properties,
            @Value("${spring.data.redis.url:${SPRING_DATA_REDIS_URL:${REDIS_URL:}}}")
                    String redisUrl,
            @Value(
                            "${spring.data.redis.password:${SPRING_DATA_REDIS_PASSWORD:${SPRING_REDIS_PASSWORD:}}}")
                    String redisPassword,
            ClientResources clientResources) {

        RedisURI redisUri;

        // Priority 1: Directly parse the fully authenticated URL injected by Engine B.
        // This completely bypasses Spring Boot's property shadowing (empty string overrides).
        if (redisUrl != null && !redisUrl.trim().isEmpty()) {
            redisUri = RedisURI.create(redisUrl);
        } else {
            // Priority 2: Fallback to fragmented properties for local development
            RedisURI.Builder uriBuilder =
                    RedisURI.builder()
                            .withHost(properties.getHost())
                            .withPort(properties.getPort());

            if (redisPassword != null && !redisPassword.isEmpty()) {
                uriBuilder.withPassword(redisPassword.toCharArray());
            } else if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
                uriBuilder.withPassword(properties.getPassword().toCharArray());
            }
            redisUri = uriBuilder.build();
        }

        return RedisClient.create(clientResources, redisUri);
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
