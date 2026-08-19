package com.example.ordersystem.dto;

import com.example.ordersystem.entity.OrderEntity;
import java.time.Instant;

public record OrderResponse(String orderId, long productId, int quantity, String status,
        Instant expiresAt, Instant paidAt) {
    public OrderResponse(String orderId, long productId, int quantity, String status) {
        this(orderId, productId, quantity, status, null, null);
    }
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(order.getOrderId(), order.getProductId(), order.getQuantity(),
                order.getStatus(), order.getExpiresAt(), order.getPaidAt());
    }
}
