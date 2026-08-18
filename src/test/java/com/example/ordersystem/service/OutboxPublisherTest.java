package com.example.ordersystem.service;

import com.example.ordersystem.entity.OutboxEventEntity;
import com.example.ordersystem.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final KafkaProducerService producer = mock(KafkaProducerService.class);
    private final RedisService redis = mock(RedisService.class);
    private final OutboxPublisher publisher = new OutboxPublisher(
            repository, producer, redis, new SimpleMeterRegistry());

    @Test
    void marksEventPublishedAfterKafkaAcknowledges() {
        OutboxEventEntity event = event();
        when(repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderById(
                eq("PENDING"), any(Instant.class))).thenReturn(List.of(event));

        publisher.publishPending();

        assertEquals("PUBLISHED", event.getStatus());
        verify(redis).setStock(7L, 8);
        verify(producer).publishOrderCreated(any());
    }

    @Test
    void retainsEventForRetryWhenKafkaIsUnavailable() {
        OutboxEventEntity event = event();
        when(repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderById(
                eq("PENDING"), any(Instant.class))).thenReturn(List.of(event));
        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(producer).publishOrderCreated(any());

        publisher.publishPending();

        assertEquals("PENDING", event.getStatus());
        assertEquals(1, event.getAttempts());
    }

    private OutboxEventEntity event() {
        return new OutboxEventEntity("event-1", "order-1", 7L, 2, 8);
    }
}
