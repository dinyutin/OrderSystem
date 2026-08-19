package com.example.ordersystem.dto;

public record AsyncOrderResponse(String requestId, String status, String orderId, String message) {}
