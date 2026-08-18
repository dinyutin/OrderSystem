package com.example.ordersystem.dto;

import com.example.ordersystem.entity.OrderEntity;

public record OrderResponse(String orderId, long productId, int quantity, String status) {
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(order.getOrderId(), order.getProductId(), order.getQuantity(), order.getStatus());
    }
}
