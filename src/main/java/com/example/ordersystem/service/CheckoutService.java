package com.example.ordersystem.service;

import com.example.ordersystem.dto.OrderResponse;
import com.example.ordersystem.dto.PaymentRequest.PaymentResult;
import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.entity.OutboxEventEntity;
import com.example.ordersystem.exception.InsufficientStockException;
import com.example.ordersystem.exception.InvalidOrderStateException;
import com.example.ordersystem.exception.OrderNotFoundException;
import com.example.ordersystem.exception.ProductNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.OutboxEventRepository;
import com.example.ordersystem.repository.ProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CheckoutService {
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OutboxEventRepository outbox;
    private final MeterRegistry metrics;

    public CheckoutService(ProductRepository products, OrderRepository orders,
            OutboxEventRepository outbox, MeterRegistry metrics) {
        this.products = products;
        this.orders = orders;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional
    public OrderResponse reserve(long productId, int quantity, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            var existing = orders.findByIdempotencyKey(key);
            if (existing.isPresent()) return OrderResponse.from(existing.get());
        }
        if (products.decrementStock(productId, quantity) == 0) {
            if (!products.existsById(productId)) throw new ProductNotFoundException(productId);
            throw new InsufficientStockException(productId, quantity);
        }
        Instant now = Instant.now();
        OrderEntity order = new OrderEntity(UUID.randomUUID().toString(), productId, quantity,
                "RESERVED", key);
        order.setExpiresAt(now.plus(5, ChronoUnit.MINUTES));
        order.setUpdatedAt(now);
        OrderEntity saved = orders.save(order);
        addEvent(saved, "ORDER_RESERVED");
        metrics.counter("checkout.reserved").increment();
        return OrderResponse.from(saved);
    }

    @Transactional
    public OrderResponse pay(String orderId, PaymentResult result) {
        OrderEntity order = orders.lockByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!"RESERVED".equals(order.getStatus())) {
            if (result == PaymentResult.SUCCESS && "COMPLETED".equals(order.getStatus())) {
                return OrderResponse.from(order);
            }
            throw new InvalidOrderStateException(orderId, order.getStatus());
        }
        if (!order.getExpiresAt().isAfter(Instant.now())) {
            expire(order);
            return OrderResponse.from(order);
        }
        if (result == PaymentResult.SUCCESS) {
            order.setStatus("COMPLETED");
            order.setPaidAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            addEvent(order, "PAYMENT_SUCCEEDED");
            metrics.counter("checkout.payment", "result", "success").increment();
        } else {
            order.setStatus("PAYMENT_FAILED");
            order.setUpdatedAt(Instant.now());
            releaseStock(order);
            addEvent(order, "PAYMENT_FAILED");
            metrics.counter("checkout.payment", "result", "failure").increment();
        }
        return OrderResponse.from(order);
    }

    @Scheduled(fixedDelayString = "${checkout.expiry-poll-ms:5000}")
    @Transactional
    public void expireReservations() {
        for (OrderEntity order : orders.lockExpiredReservations(Instant.now())) expire(order);
    }

    private void expire(OrderEntity order) {
        if (!"RESERVED".equals(order.getStatus())) return;
        order.setStatus("EXPIRED");
        order.setUpdatedAt(Instant.now());
        releaseStock(order);
        addEvent(order, "ORDER_EXPIRED");
        metrics.counter("checkout.expired").increment();
    }

    private void releaseStock(OrderEntity order) {
        products.incrementStock(order.getProductId(), order.getQuantity());
    }

    private void addEvent(OrderEntity order, String eventType) {
        int remaining = products.findById(order.getProductId()).orElseThrow().getStock();
        outbox.save(OutboxEventEntity.lifecycle(UUID.randomUUID().toString(), order.getOrderId(),
                order.getProductId(), order.getQuantity(), remaining, eventType, order.getStatus()));
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim();
        if (normalized.length() > 100) throw new IllegalArgumentException("Idempotency-Key too long");
        return normalized;
    }
}
