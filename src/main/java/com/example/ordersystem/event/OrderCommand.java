package com.example.ordersystem.event;

import java.time.Instant;

public record OrderCommand(String requestId, long productId, int quantity,
        String idempotencyKey, Instant createdAt) {}
