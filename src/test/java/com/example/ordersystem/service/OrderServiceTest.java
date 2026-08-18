package com.example.ordersystem.service;

import com.example.ordersystem.dto.OrderResponse;
import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.exception.OrderNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private final OrderTransactionService transactionService = mock(OrderTransactionService.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderService service = new OrderService(transactionService, orderRepository);

    @Test
    void delegatesOrderCreationToTransactionalService() {
        OrderResponse expected = new OrderResponse("order-1", 7L, 2, "COMPLETED");
        when(transactionService.createOrder(7L, 2)).thenReturn(expected);
        assertEquals(expected, service.createOrder(7L, 2));
    }

    @Test
    void returnsExistingOrder() {
        OrderEntity order = new OrderEntity("order-1", 7L, 2, "COMPLETED");
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));
        assertEquals("order-1", service.getOrder("order-1").orderId());
    }

    @Test
    void reportsMissingOrder() {
        when(orderRepository.findByOrderId("missing")).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class, () -> service.getOrder("missing"));
    }
}
