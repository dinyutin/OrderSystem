package com.example.ordersystem.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;



    @Column(unique = true, nullable = false)
    private String name;

    private int stock;  //

    public ProductEntity() {}

    public ProductEntity(String name, int stock) {
        this.name = name;
        this.stock = stock;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getName() {
        return name;
    }

    public void setName(String productId) {
        this.name = name;
    }
    public Long getProductId() {
        return productId;
    }

}
