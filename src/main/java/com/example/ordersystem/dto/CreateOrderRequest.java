package com.example.ordersystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @Positive long productId,
        @Min(1) @Max(100) int quantity) {
}
