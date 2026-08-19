package com.example.ordersystem.service;

import com.example.ordersystem.dto.ProductResponse;
import com.example.ordersystem.entity.ProductEntity;
import com.example.ordersystem.exception.ProductNotFoundException;
import com.example.ordersystem.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final RedisService redisService;

    public ProductService(ProductRepository productRepository, RedisService redisService) {
        this.productRepository = productRepository;
        this.redisService = redisService;
    }

    @Transactional
    public ProductEntity createProduct(String name, int stock) {
        ProductEntity saved = productRepository.save(new ProductEntity(name, stock));
        redisService.setStock(saved.getProductId(), stock);
        return saved;
    }

    @Transactional(readOnly = true)
    public ProductEntity getRequiredProduct(long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> getProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductWithCachedStock(long productId) {
        ProductEntity product = getRequiredProduct(productId);
        Integer cachedStock = redisService.getStock(productId);
        if (cachedStock == null) {
            cachedStock = product.getStock();
            redisService.setStock(productId, cachedStock);
        }
        return new ProductResponse(product.getProductId(), product.getName(), cachedStock);
    }
}
