package com.example.ordersystem.exception;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String orderId, String status) {
        super("Order " + orderId + " cannot perform this operation while status is " + status);
    }
}
