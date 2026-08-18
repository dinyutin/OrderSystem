package com.example.ordersystem.service;

import com.example.ordersystem.entity.OutboxEventEntity;
import com.example.ordersystem.event.OrderCreatedEvent;
import com.example.ordersystem.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final KafkaProducerService producer;
    private final RedisService redisService;

    public OutboxPublisher(OutboxEventRepository repository, KafkaProducerService producer,
            RedisService redisService, MeterRegistry registry) {
        this.repository = repository;
        this.producer = producer;
        this.redisService = redisService;
        registry.gauge("outbox.pending", repository, value -> value.countByStatus("PENDING"));
        registry.gauge("outbox.dead", repository, value -> value.countByStatus("DEAD"));
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEventEntity item : repository
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderById("PENDING", Instant.now())) {
            try {
                redisService.setStock(item.getProductId(), item.getRemainingStock());
                producer.publishOrderCreated(new OrderCreatedEvent(
                        item.getAggregateId(), item.getProductId(), item.getQuantity(),
                        item.getRemainingStock()));
                item.published();
            } catch (Exception exception) {
                item.retry(exception.getMessage());
                log.warn("Outbox delivery failed: eventId={}, attempt={}",
                        item.getEventId(), item.getAttempts(), exception);
            }
        }
    }
}
