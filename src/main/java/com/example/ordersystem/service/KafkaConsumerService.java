package com.example.ordersystem.service;

import com.example.ordersystem.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    @KafkaListener(topics = "order-created", groupId = "order-audit")
    public void processOrderCreated(OrderCreatedEvent event) {
        // This consumer represents downstream processing such as audit, notification,
        // or fulfillment. It never mutates stock, so redelivery cannot oversell.
        log.info("Consumed order-created event: orderId={}, productId={}, quantity={}",
                event.orderId(), event.productId(), event.quantity());
    }
}
