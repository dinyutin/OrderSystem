package com.example.ordersystem.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(long productId, int quantity) {
        super("Insufficient stock for product " + productId + ", requested quantity: " + quantity);
    }
}
