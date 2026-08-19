package com.example.ordersystem.service;

import com.example.ordersystem.event.OrderCreatedEvent;
import com.example.ordersystem.event.OrderLifecycleEvent;
import com.example.ordersystem.event.OrderCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final AsyncOrderService asyncOrders;

    public KafkaConsumerService(AsyncOrderService asyncOrders) {
        this.asyncOrders = asyncOrders;
    }

    @KafkaListener(topics = "order-commands", groupId = "order-command-workers", concurrency = "2")
    public void processOrderCommand(OrderCommand command) {
        asyncOrders.consume(command);
    }

    @KafkaListener(topics = "order-commands.DLT", groupId = "order-command-compensation")
    public void compensateFailedCommand(OrderCommand command) {
        asyncOrders.fail(command);
        log.warn("Compensated failed order command: requestId={}", command.requestId());
    }

    @KafkaListener(topics = "order-created", groupId = "order-audit")
    public void processOrderCreated(OrderCreatedEvent event) {
        // This consumer represents downstream processing such as audit, notification,
        // or fulfillment. It never mutates stock, so redelivery cannot oversell.
        log.info("Consumed order-created event: orderId={}, productId={}, quantity={}",
                event.orderId(), event.productId(), event.quantity());
    }

    @KafkaListener(topics = "order-lifecycle", groupId = "order-lifecycle-audit")
    public void processLifecycle(OrderLifecycleEvent event) {
        log.info("Consumed lifecycle event: type={}, orderId={}, status={}",
                event.eventType(), event.orderId(), event.status());
    }
}
