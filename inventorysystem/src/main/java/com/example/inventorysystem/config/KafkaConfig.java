package com.example.inventorysystem.config;

import com.example.inventorysystem.event.OutboxService;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic orderEventsTopic(){
        return TopicBuilder.name(OutboxService.ORDER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
