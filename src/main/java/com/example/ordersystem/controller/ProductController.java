package com.example.ordersystem.controller;

import com.example.ordersystem.dto.CreateProductRequest;
import com.example.ordersystem.dto.ProductResponse;
import com.example.ordersystem.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductResponse.from(productService.createProduct(request.name(), request.stock())));
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable long id) {
        return ProductResponse.from(productService.getRequiredProduct(id));
    }

    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.getProducts().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}/stock")
    public ProductResponse getStock(@PathVariable long id) {
        return productService.getProductWithCachedStock(id);
    }
}
