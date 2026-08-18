package com.example.ordersystem.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(long productId) {
        super("Product not found: " + productId);
    }
}
