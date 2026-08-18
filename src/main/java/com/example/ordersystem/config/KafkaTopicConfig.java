package com.example.ordersystem.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name("order-created").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCreatedDltTopic() {
        return TopicBuilder.name("order-created.DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxInterval(10000);
        backOff.setMaxElapsedTime(30000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }
}
