package com.example.ordersystem.service;

import com.example.ordersystem.dto.OrderResponse;
import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.entity.ProductEntity;
import com.example.ordersystem.event.OrderCreatedEvent;
import com.example.ordersystem.exception.InsufficientStockException;
import com.example.ordersystem.exception.ProductNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTransactionServiceTest {
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OrderMetrics metrics = mock(OrderMetrics.class);
    private final OrderTransactionService service =
            new OrderTransactionService(productRepository, orderRepository, publisher, metrics);

    @Test
    void atomicallyDecrementsStockCreatesOrderAndPublishesEvent() {
        ProductEntity product = new ProductEntity("Keyboard", 8);
        when(productRepository.decrementStock(10L, 2)).thenReturn(1);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.createOrder(10L, 2);

        assertEquals("COMPLETED", response.status());
        assertEquals(2, response.quantity());
        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(response.orderId(), event.getValue().orderId());
        assertEquals(8, event.getValue().remainingStock());
    }

    @Test
    void reportsMissingProduct() {
        when(productRepository.decrementStock(99L, 1)).thenReturn(0);
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThrows(ProductNotFoundException.class, () -> service.createOrder(99L, 1));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void reportsInsufficientStock() {
        when(productRepository.decrementStock(10L, 20)).thenReturn(0);
        when(productRepository.existsById(10L)).thenReturn(true);

        assertThrows(InsufficientStockException.class, () -> service.createOrder(10L, 20));
        verify(orderRepository, never()).save(any());
    }
}
