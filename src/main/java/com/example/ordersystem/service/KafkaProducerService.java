package com.example.ordersystem.service;

import com.example.ordersystem.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class KafkaProducerService {
    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
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
}
