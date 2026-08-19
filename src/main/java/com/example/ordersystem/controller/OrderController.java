package com.example.ordersystem.controller;

import com.example.ordersystem.dto.CreateOrderRequest;
import com.example.ordersystem.dto.OrderResponse;
import com.example.ordersystem.service.OrderService;
import com.example.ordersystem.service.CheckoutService;
import com.example.ordersystem.service.AsyncOrderService;
import com.example.ordersystem.dto.AsyncOrderResponse;
import com.example.ordersystem.dto.PaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final CheckoutService checkoutService;
    private final AsyncOrderService asyncOrderService;

    public OrderController(OrderService orderService, CheckoutService checkoutService,
            AsyncOrderService asyncOrderService) {
        this.orderService = orderService;
        this.checkoutService = checkoutService;
        this.asyncOrderService = asyncOrderService;
    }

    @PostMapping("/requests")
    public ResponseEntity<AsyncOrderResponse> submitAsync(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.accepted().body(asyncOrderService.submit(
                request.productId(), request.quantity(), idempotencyKey));
    }

    @GetMapping("/requests/{requestId}")
    public AsyncOrderResponse getAsyncStatus(@PathVariable String requestId) {
        return asyncOrderService.status(requestId);
    }

    @PostMapping("/reservations")
    public ResponseEntity<OrderResponse> reserve(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.reserve(
                request.productId(), request.quantity(), idempotencyKey));
    }

    @PostMapping("/{orderId}/payments")
    public OrderResponse pay(@PathVariable String orderId,
            @Valid @RequestBody PaymentRequest request) {
        return checkoutService.pay(orderId, request.result());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(request.productId(), request.quantity(), idempotencyKey));
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId);
    }
}
