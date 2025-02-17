package com.example.ordersystem.entity;

import jakarta.persistence.*;

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
    private String status;  // 訂單狀態 (Pending, Completed, Failed)

    public OrderEntity() {}

    public OrderEntity(String orderId, Long productId, String status) {
        this.orderId = orderId;
        this.productId = productId;
        this.status = status;
    }

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
