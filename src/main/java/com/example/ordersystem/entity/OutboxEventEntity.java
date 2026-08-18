package com.example.ordersystem.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;
    @Column(nullable = false, length = 36)
    private String aggregateId;
    @Column(nullable = false)
    private long productId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private int remainingStock;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant publishedAt;
    @Column(length = 500)
    private String lastError;

    protected OutboxEventEntity() {}

    public OutboxEventEntity(String eventId, String aggregateId, long productId, int quantity,
            int remainingStock) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.productId = productId;
        this.quantity = quantity;
        this.remainingStock = remainingStock;
        this.status = "PENDING";
        this.nextAttemptAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public void published() {
        status = "PUBLISHED";
        publishedAt = Instant.now();
        lastError = null;
    }

    public void retry(String error) {
        attempts++;
        status = attempts >= 10 ? "DEAD" : "PENDING";
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
        lastError = error == null ? "unknown" : error.substring(0, Math.min(500, error.length()));
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateId() { return aggregateId; }
    public long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public int getRemainingStock() { return remainingStock; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}
