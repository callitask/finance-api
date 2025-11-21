package com.treishvaam.financeapi.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CachingConfig {

    public static final String BLOG_POST_CACHE = "blogPostHtml";
    // These names MUST match the @Cacheable(value="...") in your services
    public static final String MARKET_WIDGET_CACHE = "marketWidget";
    public static final String QUOTES_BATCH_CACHE = "quotesBatch";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // Default configuration: 10 minutes TTL, JSON serialization
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // Specific configurations for different caches
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Blog posts cache: 1 hour TTL (Matches your old setup but now on Redis)
        cacheConfigurations.put(BLOG_POST_CACHE, defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Market widget cache: 5 minutes TTL (Matches the new requirement)
        cacheConfigurations.put(MARKET_WIDGET_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Global ticker cache: 5 minutes TTL (Matches the new requirement)
        cacheConfigurations.put(QUOTES_BATCH_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}