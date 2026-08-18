package com.example.ordersystem.service;

import com.example.ordersystem.dto.OrderResponse;
import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.event.OrderCreatedEvent;
import com.example.ordersystem.exception.InsufficientStockException;
import com.example.ordersystem.exception.ProductNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderTransactionService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderMetrics metrics;

    public OrderTransactionService(ProductRepository productRepository, OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher, OrderMetrics metrics) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    @Transactional
    public OrderResponse createOrder(long productId, int quantity) {
        int changedRows = productRepository.decrementStock(productId, quantity);
        if (changedRows == 0) {
            if (!productRepository.existsById(productId)) {
                metrics.rejected("product_not_found");
                throw new ProductNotFoundException(productId);
            }
            metrics.rejected("insufficient_stock");
            throw new InsufficientStockException(productId, quantity);
        }

        OrderEntity order = new OrderEntity(
                UUID.randomUUID().toString(), productId, quantity, "COMPLETED");
        OrderEntity saved = orderRepository.save(order);
        int remainingStock = productRepository.findById(productId).orElseThrow().getStock();

        eventPublisher.publishEvent(new OrderCreatedEvent(
                saved.getOrderId(), productId, quantity, remainingStock));
        metrics.created();
        return OrderResponse.from(saved);
    }
}
