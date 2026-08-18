package com.example.ordersystem.service;

import com.example.ordersystem.dto.OrderResponse;
import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.entity.OutboxEventEntity;
import com.example.ordersystem.exception.InsufficientStockException;
import com.example.ordersystem.exception.ProductNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderTransactionService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final OrderMetrics metrics;

    public OrderTransactionService(ProductRepository productRepository, OrderRepository orderRepository,
            OutboxEventRepository outboxRepository, OrderMetrics metrics) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.metrics = metrics;
    }

    @Transactional
    public OrderResponse createOrder(long productId, int quantity) {
        return createOrder(productId, quantity, null);
    }

    @Transactional
    public OrderResponse createOrder(long productId, int quantity, String idempotencyKey) {
        String normalizedKey = normalizeKey(idempotencyKey);
        if (normalizedKey != null) {
            var existing = orderRepository.findByIdempotencyKey(normalizedKey);
            if (existing.isPresent()) {
                return OrderResponse.from(existing.get());
            }
        }
        int changedRows = productRepository.decrementStock(productId, quantity);
        if (changedRows == 0) {
            if (!productRepository.existsById(productId)) {
                metrics.rejected("product_not_found");
                throw new ProductNotFoundException(productId);
            }
            metrics.rejected("insufficient_stock");
            throw new InsufficientStockException(productId, quantity);
        }

        OrderEntity order = new OrderEntity(UUID.randomUUID().toString(), productId, quantity,
                "COMPLETED", normalizedKey);
        OrderEntity saved = orderRepository.save(order);
        int remainingStock = productRepository.findById(productId).orElseThrow().getStock();

        outboxRepository.save(new OutboxEventEntity(UUID.randomUUID().toString(),
                saved.getOrderId(), productId, quantity, remainingStock));
        metrics.created();
        return OrderResponse.from(saved);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 100 characters");
        }
        return normalized;
    }
}
