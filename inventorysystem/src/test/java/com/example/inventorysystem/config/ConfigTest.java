package com.example.inventorysystem.config;

import com.example.inventorysystem.config.CacheConfig;
import com.example.inventorysystem.config.KafkaConfig;
import com.example.inventorysystem.config.RedisConfig;
import com.example.inventorysystem.event.OutboxService;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigTest {

    @Test
    void cacheErrorHandlerNeverPropagatesCacheFailures() {
        CacheErrorHandler handler = new CacheConfig().errorHandler();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("products");
        RuntimeException boom = new RuntimeException("redis down");

        assertThatCode(() -> {
            handler.handleCacheGetError(boom, cache, 1L);
            handler.handleCachePutError(boom, cache, 1L, "value");
            handler.handleCacheEvictError(boom, cache, 1L);
            handler.handleCacheClearError(boom, cache);
        }).doesNotThrowAnyException();
    }

    @Test
    void redisCacheManagerIsBuilt() {
        var manager = new RedisConfig().cacheManager(mock(RedisConnectionFactory.class), 5);

        assertThat(manager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    void orderEventsTopicHasThreePartitions() {
        NewTopic topic = new KafkaConfig().orderEventsTopic();

        assertThat(topic.name()).isEqualTo(OutboxService.ORDER_EVENTS_TOPIC);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
