package com.example.ordersystem.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {
    private final Counter created;
    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.created = Counter.builder("orders.successful")
                .description("Successfully created orders")
                .register(registry);
    }

    public void created() {
        created.increment();
    }

    public void rejected(String reason) {
        registry.counter("orders.rejected", "reason", reason).increment();
    }
}
