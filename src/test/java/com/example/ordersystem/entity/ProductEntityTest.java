package com.example.ordersystem.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductEntityTest {
    @Test
    void updatesName() {
        ProductEntity product = new ProductEntity("Old", 1);
        product.setName("New");
        assertEquals("New", product.getName());
    }
}
