package com.example.inventorysystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${inventory.cache.product-ttl-minutes:10}") long productTtlMinutes
    ){
        RedisCacheConfiguration defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(productTtlMinutes))
                        .disableCachingNullValues()
                        .prefixCacheNameWith("inventory::");

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaults)
                .build();
    }
}
