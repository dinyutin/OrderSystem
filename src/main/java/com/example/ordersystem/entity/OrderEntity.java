package com.example.ordersystem.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 訂單主鍵

    @Column(unique = true, nullable = false)
    private String orderId;  // 訂單 UUID

    @Column(nullable = false)
    private Long productId;  // 商品 ID

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private String status;  // 訂單狀態 (Pending, Completed, Failed)

    @Column(unique = true, length = 100)
    private String idempotencyKey;

    private Instant expiresAt;
    private Instant paidAt;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public OrderEntity() {}

    public OrderEntity(String orderId, Long productId, int quantity, String status) {
        this(orderId, productId, quantity, status, null);
    }

    public OrderEntity(String orderId, Long productId, int quantity, String status, String idempotencyKey) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
