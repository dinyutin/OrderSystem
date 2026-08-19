package com.example.ordersystem.service;

import com.example.ordersystem.event.OrderCreatedEvent;
import com.example.ordersystem.event.OrderLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class KafkaProducerService {
    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            var result = kafkaTemplate.send("order-created", event.orderId(), event)
                    .get(Duration.ofSeconds(3).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            log.info("Published order-created event: orderId={}, partition={}, offset={}",
                    event.orderId(), result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka publish failed", exception);
        }
    }

    public void publishLifecycle(OrderLifecycleEvent event) {
        try {
            var result = kafkaTemplate.send("order-lifecycle", event.orderId(), event)
                    .get(Duration.ofSeconds(3).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            log.info("Published lifecycle event: type={}, orderId={}, partition={}, offset={}",
                    event.eventType(), event.orderId(), result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka lifecycle publish failed", exception);
        }
    }
}
