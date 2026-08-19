package com.example.ordersystem.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentRequest(@NotNull PaymentResult result) {
    public enum PaymentResult { SUCCESS, FAILURE }
}
