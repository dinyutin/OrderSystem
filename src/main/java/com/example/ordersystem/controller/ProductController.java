package com.example.ordersystem.controller;

import com.example.ordersystem.entity.ProductEntity;
import com.example.ordersystem.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/create")
    public ResponseEntity<ProductEntity> createProduct(
            @RequestParam String name,
            @RequestParam int stock) {
        ProductEntity product = productService.createProduct(name, stock);
        return ResponseEntity.ok(product);
    }
    @GetMapping("/{id}")
    public ResponseEntity<ProductEntity> getProductById(@PathVariable Long id) {
        Optional<ProductEntity> productOpt = productService.findProductById(id);
        return productOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
