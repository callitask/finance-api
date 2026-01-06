package com.treishvaam.financeapi.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CachingConfig implements CachingConfigurer {

  private static final Logger logger = LoggerFactory.getLogger(CachingConfig.class);

  public static final String BLOG_POST_CACHE = "blogPostHtml";
  public static final String MARKET_WIDGET_CACHE = "marketWidget";
  public static final String QUOTES_BATCH_CACHE = "quotesBatch";

  @Autowired private RedisConnectionFactory redisConnectionFactory;

  @Override
  public CacheManager cacheManager() {
    // 1. Configure ObjectMapper with JavaTimeModule for LocalDateTime support
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());

    // ENTERPRISE FIX: Fail Safe Deserialization
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Enable polymorphic type handling so Redis knows which class to deserialize into
    objectMapper.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);

    // 2. Create a serializer using this specific ObjectMapper
    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);

    // 3. Configure Redis Cache to use this serializer
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    // Specific configurations
    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
    cacheConfigurations.put(BLOG_POST_CACHE, defaultConfig.entryTtl(Duration.ofHours(1)));
    cacheConfigurations.put(MARKET_WIDGET_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));
    cacheConfigurations.put(QUOTES_BATCH_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new SimpleCacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        logger.warn(
            "Cache GET failure in cache '{}' for key '{}'. Treating as Cache Miss. Error: {}",
            cache.getName(),
            key,
            exception.getMessage());
      }

      @Override
      public void handleCachePutError(
          RuntimeException exception, Cache cache, Object key, Object value) {
        logger.warn(
            "Cache PUT failure in cache '{}' for key '{}'. Error: {}",
            cache.getName(),
            key,
            exception.getMessage());
      }

      @Override
      public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        logger.warn(
            "Cache EVICT failure in cache '{}' for key '{}'. Error: {}",
            cache.getName(),
            key,
            exception.getMessage());
      }

      @Override
      public void handleCacheClearError(RuntimeException exception, Cache cache) {
        logger.warn(
            "Cache CLEAR failure in cache '{}'. Error: {}",
            cache.getName(),
            exception.getMessage());
      }
    };
  }
}
