package com.example.inventorysystem.config;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log=
            LoggerFactory.getLogger(CacheConfig.class);

    public CacheErrorHandler errorHandler(){
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed ({}:{}); using database",
                cache.getName(),key,exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value) {
                log.warn("Cache PUT failed ({}:{})",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed ({}:{}); entry expires by TTL",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                    log.warn("Cache CLEAR failed ({})", cache.getName(), exception);
            }
        };
    }
}
