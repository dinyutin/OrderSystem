package com.example.ordersystem.service;

import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.entity.ProductEntity;
import com.example.ordersystem.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final KafkaProducerService kafkaProducerService;

    public OrderService(OrderRepository orderRepository, ProductService productService, KafkaProducerService kafkaProducerService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Transactional
    public String createOrder(long productId) {
        // 先檢查庫存是否足夠
        Optional<ProductEntity> productOpt = productService.findProductById(productId);
        if (productOpt.isEmpty() || productOpt.get().getStock() <= 0) {
            return "庫存不足，搶購失敗！";
        }

        // 減少庫存
        boolean success = productService.reduceStock(productId);
        if (!success) {
            return "庫存不足，搶購失敗！";
        }

        // 創建訂單
        OrderEntity order = new OrderEntity();
        order.setOrderId(UUID.randomUUID().toString());
        order.setProductId(Long.valueOf(productId));
        order.setStatus("Pending");

        orderRepository.save(order);

        // 發送 Kafka 訊息
        kafkaProducerService.sendOrder(order.getOrderId());

        return order.getOrderId();
    }
}
