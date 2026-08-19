package com.example.ordersystem.service;

import com.example.ordersystem.dto.AsyncOrderResponse;
import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.entity.OutboxEventEntity;
import com.example.ordersystem.event.OrderCommand;
import com.example.ordersystem.exception.InsufficientStockException;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.OutboxEventRepository;
import com.example.ordersystem.repository.ProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AsyncOrderService {
    private final RedisService redis;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry metrics;

    public AsyncOrderService(RedisService redis, ProductRepository products, OrderRepository orders,
            OutboxEventRepository outbox, KafkaTemplate<String, Object> kafka, MeterRegistry metrics) {
        this.redis = redis; this.products = products; this.orders = orders;
        this.outbox = outbox; this.kafka = kafka; this.metrics = metrics;
    }

    public AsyncOrderResponse submit(long productId, int quantity, String idempotencyKey) {
        String requestId = UUID.randomUUID().toString();
        if (!redis.reserve(productId, quantity, requestId)) {
            throw new InsufficientStockException(productId, quantity);
        }
        try {
            kafka.send("order-commands", requestId,
                    new OrderCommand(requestId, productId, quantity, idempotencyKey, Instant.now())).get();
            metrics.counter("async.orders.submitted").increment();
            return new AsyncOrderResponse(requestId, "PROCESSING", null, "訂單已進入處理佇列");
        } catch (Exception exception) {
            redis.compensate(productId, quantity, requestId, "QUEUE_FAILED");
            metrics.counter("async.orders.failed", "stage", "enqueue").increment();
            throw new IllegalStateException("訂單佇列暫時無法使用", exception);
        }
    }

    public AsyncOrderResponse status(String requestId) {
        return redis.getRequest(requestId);
    }

    public void fail(OrderCommand command) {
        redis.compensate(command.productId(), command.quantity(), command.requestId(), "FAILED");
        metrics.counter("async.orders.failed", "stage", "consumer").increment();
    }

    @Transactional
    public void consume(OrderCommand command) {
        if (!"PROCESSING".equals(redis.getRequest(command.requestId()).status())) return;
        if (products.decrementStock(command.productId(), command.quantity()) == 0) {
            redis.compensate(command.productId(), command.quantity(), command.requestId(), "FAILED");
            metrics.counter("async.orders.failed", "stage", "database_stock").increment();
            return;
        }
        Instant now = Instant.now();
        OrderEntity order = new OrderEntity(UUID.randomUUID().toString(), command.productId(),
                command.quantity(), "RESERVED", command.idempotencyKey());
        order.setExpiresAt(now.plus(5, ChronoUnit.MINUTES));
        order.setUpdatedAt(now);
        orders.save(order);
        int remaining = products.findById(command.productId()).orElseThrow().getStock();
        outbox.save(OutboxEventEntity.lifecycle(UUID.randomUUID().toString(), order.getOrderId(),
                order.getProductId(), order.getQuantity(), remaining, "ORDER_RESERVED", order.getStatus()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redis.completeRequest(command.requestId(), order.getOrderId());
                metrics.counter("async.orders.completed").increment();
            }
        });
    }
}
