package com.example.ordersystem.event;

import java.time.Instant;

public record OrderLifecycleEvent(String eventType, String orderId, long productId, int quantity,
        String status, Instant occurredAt) {}
