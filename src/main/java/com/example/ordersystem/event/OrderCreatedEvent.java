package com.example.ordersystem.event;

public record OrderCreatedEvent(
        String orderId,
        long productId,
        int quantity,
        int remainingStock) {
}
