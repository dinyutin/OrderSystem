package com.example.ordersystem.event;

import com.example.ordersystem.service.KafkaProducerService;
import com.example.ordersystem.service.RedisService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderCreatedEventHandler {
    private final RedisService redisService;
    private final KafkaProducerService kafkaProducerService;

    public OrderCreatedEventHandler(RedisService redisService, KafkaProducerService kafkaProducerService) {
        this.redisService = redisService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterOrderCommit(OrderCreatedEvent event) {
        redisService.setStock(event.productId(), event.remainingStock());
        kafkaProducerService.publishOrderCreated(event);
    }
}
