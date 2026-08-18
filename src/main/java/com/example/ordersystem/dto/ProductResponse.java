package com.example.ordersystem.dto;

import com.example.ordersystem.entity.ProductEntity;

public record ProductResponse(long productId, String name, int stock) {
    public static ProductResponse from(ProductEntity product) {
        return new ProductResponse(product.getProductId(), product.getName(), product.getStock());
    }
}
