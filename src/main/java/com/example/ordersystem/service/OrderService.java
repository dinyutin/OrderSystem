package com.example.ordersystem.service;

import com.example.ordersystem.dto.OrderResponse;
import com.example.ordersystem.exception.OrderNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderTransactionService transactionService;
    private final OrderRepository orderRepository;

    public OrderService(OrderTransactionService transactionService, OrderRepository orderRepository) {
        this.transactionService = transactionService;
        this.orderRepository = orderRepository;
    }

    public OrderResponse createOrder(long productId, int quantity) {
        return transactionService.createOrder(productId, quantity);
    }

    public OrderResponse getOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
