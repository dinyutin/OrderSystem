package com.example.ordersystem.service;

import com.example.ordersystem.entity.OrderEntity;
import com.example.ordersystem.entity.ProductEntity;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class KafkaConsumerService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final RedisService redisService;

    public KafkaConsumerService(OrderRepository orderRepository, ProductRepository productRepository, ProductService productService,
            RedisService redisService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.redisService = redisService;
    }

    /**
     * 監聽 Kafka 訂單消息，解析後存入 MySQL
     */
    @KafkaListener(topics = "order-topic", groupId = "order-group")
    @Transactional
    public void processOrder(String orderMessage) {
        System.out.println("收到訂單訊息：" + orderMessage);

        // 訂單訊息格式: "orderId:productId"
        String[] parts = orderMessage.split(":");
        if (parts.length != 2) {
            System.out.println("訂單格式錯誤：" + orderMessage);
            return;
        }

        String orderId = parts[0];
        long productId = Long.parseLong(parts[1]);

        // **先從 Redis 檢查庫存**
        boolean stockReduced = productService.reduceStock(productId);
        if (!stockReduced) {
            System.out.println("商品庫存不足，無法處理訂單：" + orderId);
            return;
        }

        // **確認 MySQL 的庫存是否仍足夠，避免 Redis 數據不同步**
        int rowsUpdated = productRepository.updateStock(productId);
        if (rowsUpdated == 0) {
            System.out.println("MySQL 庫存更新失敗 (可能 Redis 與 MySQL 不一致)，productId：" + productId);
            redisService.incrementStock("product_stock_" + productId);  // **補回 Redis**
            return;
        }

        // **成功扣減 MySQL 庫存，記錄訂單**
        Optional<ProductEntity> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            OrderEntity order = new OrderEntity(orderId, productId, "Completed");
            orderRepository.save(order);

            System.out.println("訂單已存入 MySQL：" + orderId);
        } else {
            System.out.println("訂單處理失敗，無法找到商品：" + productId);
        }
    }

    /**
     * 監聽 Kafka `stock-update-topic` 更新 MySQL 庫存
     */
    @KafkaListener(topics = "stock-update-topic", groupId = "stock-group")
    @Transactional
    public void processStockUpdate(String stockMessage) {
        System.out.println("收到庫存更新訊息：" + stockMessage);

        // 訊息格式: "productId:remainingStock"
        String[] parts = stockMessage.split(":");
        if (parts.length != 2) {
            System.out.println("庫存更新訊息格式錯誤：" + stockMessage);
            return;
        }

        long productId = Long.parseLong(parts[0]);
        int newStock = Integer.parseInt(parts[1]);

        // 查詢商品
        Optional<ProductEntity> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            System.out.println("產品不存在，無法更新庫存：" + productId);
            return;
        }

        ProductEntity product = productOpt.get();
        product.setStock(newStock);
        productRepository.save(product);

        System.out.println("MySQL 庫存已更新，productId：" + productId + "，新庫存：" + newStock);
    }
}
