package com.treishvaam.financeapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CachingConfig {

    public static final String BLOG_POST_CACHE = "blogPostHtml";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(BLOG_POST_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.DAYS) // Set a TTL for safety
                .maximumSize(500)); // Max number of pages to cache
        return cacheManager;
    }
}