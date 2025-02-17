package com.example.ordersystem.controller;

import com.example.ordersystem.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create/{productId}")
    public ResponseEntity<String> createOrder(@PathVariable long productId) {
        String result = orderService.createOrder(productId);
        return ResponseEntity.ok(result);
    }
}
