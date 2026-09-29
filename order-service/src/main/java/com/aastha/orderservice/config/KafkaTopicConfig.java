package com.aastha.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

public class KafkaTopicConfig {

    @Value("${app.kafka.topics.order-events}")
    private String orderEventsTopic;

    @Value("${app.kafka.topics.order-events-dlq}")
    private String orderEventsDlqTopic;

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderEventsDlqTopic() {
        return TopicBuilder.name(orderEventsDlqTopic).partitions(1).replicas(1).build();
    }
}
