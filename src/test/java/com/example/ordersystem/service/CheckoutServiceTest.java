package com.example.ordersystem.service;

import com.example.ordersystem.dto.PaymentRequest.PaymentResult;
import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.entity.ProductEntity;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.OutboxEventRepository;
import com.example.ordersystem.repository.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final CheckoutService service = new CheckoutService(products, orders, outbox,
            new SimpleMeterRegistry());

    @Test
    void reservesStockUntilPaymentCompletes() {
        ProductEntity product = new ProductEntity("Ticket", 9);
        when(products.decrementStock(1L, 1)).thenReturn(1);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));

        var reserved = service.reserve(1L, 1, "checkout-1");

        assertEquals("RESERVED", reserved.status());
        verify(products).decrementStock(1L, 1);
        verify(outbox).save(any());
    }

    @Test
    void failedPaymentReleasesStockOnce() {
        ProductEntity product = new ProductEntity("Ticket", 10);
        OrderEntity order = reservedOrder();
        when(orders.lockByOrderId("order-1")).thenReturn(Optional.of(order));
        when(products.findById(1L)).thenReturn(Optional.of(product));

        var response = service.pay("order-1", PaymentResult.FAILURE);

        assertEquals("PAYMENT_FAILED", response.status());
        verify(products).incrementStock(1L, 1);
    }

    @Test
    void successfulPaymentIsIdempotent() {
        ProductEntity product = new ProductEntity("Ticket", 9);
        OrderEntity order = reservedOrder();
        when(orders.lockByOrderId("order-1")).thenReturn(Optional.of(order));
        when(products.findById(1L)).thenReturn(Optional.of(product));

        assertEquals("COMPLETED", service.pay("order-1", PaymentResult.SUCCESS).status());
        assertEquals("COMPLETED", service.pay("order-1", PaymentResult.SUCCESS).status());
        verify(products, never()).incrementStock(any(Long.class), any(Integer.class));
    }

    @Test
    void expiryWorkerReleasesReservedStock() {
        ProductEntity product = new ProductEntity("Ticket", 10);
        OrderEntity order = reservedOrder();
        order.setExpiresAt(Instant.now().minusSeconds(1));
        when(orders.lockExpiredReservations(any())).thenReturn(List.of(order));
        when(products.findById(1L)).thenReturn(Optional.of(product));

        service.expireReservations();

        assertEquals("EXPIRED", order.getStatus());
        verify(products).incrementStock(1L, 1);
    }

    private OrderEntity reservedOrder() {
        OrderEntity order = new OrderEntity("order-1", 1L, 1, "RESERVED", "checkout-1");
        order.setExpiresAt(Instant.now().plusSeconds(60));
        return order;
    }
}
